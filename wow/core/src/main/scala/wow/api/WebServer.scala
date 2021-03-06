package wow.api

import akka.http.scaladsl.server.{HttpApp, Route}
import wow.utils.Reflection

/**
  * This object implements a web server which interacts with the database.
  */
trait API {
  val route: Route
}

object WebServer extends HttpApp {
  private val apis = Reflection.objectsOf[API]

  println(s"API components: ${apis map (_.getClass.getName) reduceLeft (_ + ", " + _)}")

  /**
    * Retrieves a list of objects implementing the trait API.
    */
  override val route: Route = {
    apis map (_.route) reduce (_ ~ _)
  }
}

case class WebServerConfiguration(host: String, port: Int)
