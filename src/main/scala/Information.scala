import play.api.libs.json.{JsArray, JsValue}

import scala.util.Random

case class Information(name: String, gitHubMetrics: JsValue, evaluationMetrics: JsValue, metaData: JsValue) {
  val mstars = Random.nextInt(30)
  def stars(): Int = mstars// (gitHubMetrics \ "starsCount").as[Int]
  def testCoverage():  Double = 0.9// (evaluationMetrics \ "tests").as[Double]
  def releases(): Int = {
    3
  }
  def sumCommitsTop3Contributors(): Int = {
    160
   /* val contributors = (gitHubMetrics \ "contributors").as[JsArray]
    val sorted = contributors.value.sortWith { (a, b) => (a \ "commitsCount").as[Int] > (b \ "commitsCount").as[Int] }
    (sorted(0) \ "commitsCount").as[Int] + (sorted(1) \ "commitsCount").as[Int] + (sorted(2) \ "commitsCount").as[Int]*/
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