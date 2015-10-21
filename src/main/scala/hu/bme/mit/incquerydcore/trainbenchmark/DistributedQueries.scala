package hu.bme.mit.incquerydcore.trainbenchmark

import java.io.{ObjectInputStream, ObjectOutputStream, IOException}

import akka.actor._
import akka.remote.RemoteScope
import hu.bme.mit.incquerydcore._

import scala.collection.immutable.HashMap

/**
 * Created by wafle on 10/20/2015.
 */
abstract class DistributedQuery extends RemoteOnlyTrainbenchmarkQuery with Serializable {
  override val system = RemoteOnlyTrainbenchmarkQuery.system
  val remote2Adress = Address("akka.tcp", "Slave2System", System.getenv("SLAVE2IP"), 2552)
  val remote3Adress = Address("akka.tcp", "Slave3System", System.getenv("SLAVE3IP"), 2552)

  def newRemote2(props: Props, name: String): ActorRef = {
    val actor = system.actorOf(props.withDeploy(Deploy(scope = RemoteScope(remote2Adress))), name + System.currentTimeMillis().toString)
    actors += actor
    actor
  }

  def newRemote3(props: Props, name: String): ActorRef = {
    val actor = system.actorOf(props.withDeploy(Deploy(scope = RemoteScope(remote3Adress))), name + System.currentTimeMillis().toString)
    actors += actor
    actor
  }

  @throws(classOf[IOException])
  private def writeObject(out: ObjectOutputStream): Unit = {}

  @throws(classOf[IOException])
  private def readObject(in: ObjectInputStream): Unit = {}

}
class DistributedRouteSensor extends  DistributedQuery {
  val production = newLocal(Props(new Production("RouteSensor")))
  val antijoin = newRemote3(Props(new HashAntiJoiner(production ! _, Vector(2, 3), Vector(0, 1))), "RouteSensor-antijoin")
  val sensorJoin = newRemote2(Props(new HashJoiner(antijoin ! Primary(_), 3, Vector(1), 2, Vector(0))), "RouteSensor-sensor=join")
  val followsJoin = newRemote1(Props(new HashJoiner(sensorJoin ! Primary(_), 2, Vector(0), 2, Vector(1))), "RouteSensor-follows-join")
  val inputLookup = HashMap(
    "switch" -> ((cs: ChangeSet) => followsJoin ! Primary(cs)),
    "follows" -> ((cs:ChangeSet) => followsJoin ! Secondary(cs)),
    "sensor" -> ((cs: ChangeSet) => sensorJoin ! Secondary(cs)),
    "definedBy" -> ((cs: ChangeSet) => antijoin ! Secondary(cs))
  )
  import utils.ReteNode
  val inputNodes: List[ReteMessage => Unit] =
    List(
      antijoin.secondary, sensorJoin.secondary,
      followsJoin.primary, followsJoin.secondary
    )
  override val terminator = Terminator(inputNodes, production)
}

class DistributedSplitRouteSensor extends  DistributedQuery {
  import utils.ReteNode
  val production = newLocal(Props(new Production("RouteSensor")))
  val antijoin = newRemote1(Props(new HashAntiJoiner(production ! _, Vector(2, 3), Vector(0, 1), expectedTerminatorCount = 2)), "RouteSensor-antijoin")
  val sensorJoinA = newRemote2(Props(new HashJoiner(antijoin.primary, 3, Vector(1), 2, Vector(0))), "RouteSensor-sensor=join-A")
  val sensorJoinB = newRemote3(Props(new HashJoiner(antijoin.primary, 3, Vector(1), 2, Vector(0))), "RouteSensor-sensor=join-B")

  val followsJoin = newRemote1(Props(
    new ParallelHashJoiner(
      Vector(sensorJoinA.primary,sensorJoinB.primary)
      , 2, Vector(0), 2, Vector(1),
      hashFunction = n => n(1).hashCode()
    )), "RouteSensor-follows-join")

  val inputLookup = HashMap(
    "switch" -> ((cs: ChangeSet) => followsJoin ! Primary(cs)),
    "follows" -> ((cs:ChangeSet) => followsJoin ! Secondary(cs)),
    "sensor" -> ((cs: ChangeSet) =>
      {
        val children = Array(sensorJoinA, sensorJoinB)
        cs.positive.groupBy( tup => Math.abs(tup.hashCode()) % 2).foreach(kv => if (kv._2.size > 0) children(kv._1) ! Secondary(ChangeSet(positive = kv._2.toVector)))
        cs.negative.groupBy( tup => Math.abs(tup.hashCode()) % 2).foreach(kv => if (kv._2.size > 0) children(kv._1) ! Secondary(ChangeSet(positive = kv._2.toVector)))
      }),
    "definedBy" -> ((cs: ChangeSet) => antijoin ! Secondary(cs))
  )

  val inputNodes: List[ReteMessage => Unit] =
    List(
      antijoin.secondary,
      sensorJoinA.secondary, sensorJoinB.secondary,
      followsJoin.primary, followsJoin.secondary
    )
  override val terminator = Terminator(inputNodes, production)
}