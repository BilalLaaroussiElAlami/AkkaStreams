import Main.{Package, apiRequest}
import play.api.libs.json.{JsArray, JsValue}

import scala.util.Random

case class Release(from: String, to: String, count: Int)
case class Information(name: String, gitHubMetrics: JsValue, evaluationMetrics: JsValue, metaData: JsValue) {
  def stars(): Int =  (gitHubMetrics \ "starsCount").as[Int]

  def testCoverage():  Double =  (evaluationMetrics \ "quality" \ "tests").as[Double]

  def releases(): Int = (metaData \ "releases").as[JsArray].value.map(release => (release \ "count").as[Int]).sum

  def sumCommitsTop3Contributors(): Int = {
    val contributors = (gitHubMetrics \ "contributors").as[JsArray]
    val sorted = contributors.value.sortWith { (a, b) => (a \ "commitsCount").as[Int] > (b \ "commitsCount").as[Int] }
    (0 until Math.min(3, sorted.length)).map(i => (sorted(i) \ "commitsCount").as[Int]).sum

    //(sorted(0) \ "commitsCount").as[Int] + (sorted(1) \ "commitsCount").as[Int] + (sorted(2) \ "commitsCount").as[Int]
  }

  override def toString() = {
    f"name = ${name}\nstars = ${stars()}\ntestcoverage = ${testCoverage()}\nreleases = ${releases()}\nsumCommitstop3 = ${sumCommitsTop3Contributors()}"
  }
}
object Information {
  def fromJson(json: JsValue) = {
    val name = (json \ "collected" \ "metadata" \ "name").as[String]
    val gitHubMetrics = (json \ "collected" \ "github").as[JsValue]
    val evaluationMetrics = (json \ "evaluation").as[JsValue]
    val metadata = (json \ "collected" \ "metadata").as[JsValue]
    apply(name, gitHubMetrics, evaluationMetrics, metadata)
  }
}



object TestInformation extends App{
  val InfoHashy =  Information.fromJson(apiRequest(Package("hashy")))
  println(InfoHashy);println()
  val InfoHapi =  Information.fromJson(apiRequest(Package("hapi-decorators")))
  println(InfoHapi);println()
  val InfoHapiRoutes =  Information.fromJson(apiRequest(Package("hapi-routes")))
  println(InfoHapiRoutes);println()
  val InfoHashMap =  Information.fromJson(apiRequest(Package("hashmap")))
  println(InfoHashMap);println()
  val InfoLowerCase =  Information.fromJson(apiRequest(Package("lower-case")))
  println(InfoLowerCase);println()
}

