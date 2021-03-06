import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import hu.bme.mit.ire.datatypes.Tuple
import hu.bme.mit.ire.messages.ChangeSet
import hu.bme.mit.ire.nodes.unary.{AverageNode, CollectNode, CountNode, SumNode}
import hu.bme.mit.ire.util.TestUtil._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration.Duration

class AggregationNodeTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  // 0: city, 1: name, 2: weapon, 3: sex, 4: height
  val odin: Tuple = Vector("Asgard", "Odin", "Gungnir", "male", 1)
  val thor: Tuple = Vector("Asgard", "Thor", "Mjölnir", "male", 1.1f)
  val freya: Tuple = Vector("Asgard", "Freya", "N/A", "female", 1.1)
  val geirröd: Tuple = Vector("Jötunheimr", "Geirröd ", "N/A", "male", 2L)
  val ragnar: Tuple = Vector("Midgard", "Ragnarr Loðbrók", "N/A", "male", 0.8)

  def this() = this(ActorSystem("MySpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Count" should {
    "count with complex keys" in {
      val echoActor = system.actorOf(TestActors.echoActorProps)
      val counter = system.actorOf(Props(new CountNode(echoActor ! _, Vector(3, 0)))) // sex and the city
      counter ! ChangeSet(positive = Vector(odin))
      expectMsg(ChangeSet(positive = Vector(Vector("male", "Asgard", 1))))
      counter ! ChangeSet(positive = Vector(thor))
      expectMsg(ChangeSet(
        positive = Vector(Vector("male", "Asgard", 2)),
        negative = Vector(Vector("male", "Asgard", 1))
      ))
      counter ! ChangeSet(negative = Vector(odin))
      expectMsg(ChangeSet(
        positive = Vector(Vector("male", "Asgard", 1)),
        negative = Vector(Vector("male", "Asgard", 2))
      ))
      counter ! ChangeSet(positive = Vector(freya))
      expectMsg(ChangeSet(positive = Vector(Vector("female", "Asgard", 1))))
      counter ! ChangeSet(negative = Vector(freya))
      expectMsg(ChangeSet(negative = Vector(Vector("female", "Asgard", 1))))
    }
  }

  "Collect" should {
    "collect with complex keys" in {
      val echoActor = system.actorOf(TestActors.echoActorProps)
      val counter = system.actorOf(Props(new CollectNode(echoActor ! _, Vector(3, 0), 2))) // (sex, city): (weapon)
      counter ! ChangeSet(positive = Vector(odin))
      expectMsg(ChangeSet(positive = Vector(Vector("male", "Asgard", cypherList("Gungnir")))))
      counter ! ChangeSet(positive = Vector(thor))
      expectMsg(ChangeSet(
        positive = Vector(Vector("male", "Asgard", cypherList("Gungnir", "Mjölnir"))),
        negative = Vector(Vector("male", "Asgard", cypherList("Gungnir")))
      ))
      counter ! ChangeSet(negative = Vector(odin))
      expectMsg(ChangeSet(
        positive = Vector(Vector("male", "Asgard", cypherList("Mjölnir"))),
        negative = Vector(Vector("male", "Asgard", cypherList("Gungnir", "Mjölnir")))
      ))
      counter ! ChangeSet(positive = Vector(freya))
      expectMsg(ChangeSet(positive = Vector(Vector("female", "Asgard", cypherList("N/A")))))
      counter ! ChangeSet(negative = Vector(freya))
      expectMsg(ChangeSet(negative = Vector(Vector("female", "Asgard", cypherList("N/A")))))
    }
  }

  def assertNextChangeSetWithTolerance(key: Int, positive: Option[Any] = None, negative: Option[Any] = None): Unit = {
    val cs = receiveOne(Duration("1 s")).asInstanceOf[ChangeSet]

    def assertEquals(actual: Any, expected: Any) {
      (actual, expected) match {
        case (actual: Double, expected: Double) => actual should be(expected +- 0.01)
        case (actual: Float, expected: Float) => actual should be(expected +- 0.01f)
        case _ => actual should be(expected)
      }
    }
    if (positive.isDefined) {
      assertEquals(cs.positive(0)(key), positive.get)
    }

    if (negative.isDefined) {
      assertEquals(cs.negative(0)(key), negative.get)
    }
  }

  "Sum" should {
    "sum with complex keys" in {
      val echoActor = system.actorOf(TestActors.echoActorProps)
      val counter = system.actorOf(Props(new SumNode(echoActor ! _, Vector(3), 4))) // sex, sum for height
      counter ! ChangeSet(positive = Vector(odin))
      assertNextChangeSetWithTolerance(key = 1, positive = Some(1))
      counter ! ChangeSet(positive = Vector(thor))
      assertNextChangeSetWithTolerance(key = 1, positive = Some(2.1f), negative = Some(1))
      counter ! ChangeSet(positive = Vector(ragnar))
      assertNextChangeSetWithTolerance(key = 1, positive = Some(2.9), negative = Some(2.1f))
    }
  }

  "Average" should {
    "average with complex keys" in {
      val echoActor = system.actorOf(TestActors.echoActorProps)
      val counter = system.actorOf(Props(new AverageNode(echoActor ! _, Vector(3), 4))) // sex, sum for height
      counter ! ChangeSet(positive = Vector(odin))
      assertNextChangeSetWithTolerance(key = 1, positive = Some(1))
      counter ! ChangeSet(positive = Vector(thor))
      assertNextChangeSetWithTolerance(key = 1, positive = Some(2.1f/2), negative = Some(1))
      counter ! ChangeSet(positive = Vector(ragnar))
      assertNextChangeSetWithTolerance(key = 1, positive = Some(2.9/3), negative = Some(2.1f/2))
    }
  }

}
