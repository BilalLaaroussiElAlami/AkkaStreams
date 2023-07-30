
import akka.actor.ActorSystem
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Balance, Broadcast, Compression, FileIO, Flow, Framing, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{FlowShape, Graph, IOResult, OverflowStrategy}
import akka.util.ByteString
import akka.{Done, NotUsed}
import play.api.libs.json.{JsValue, Json}

import java.io.FileWriter
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.{APPEND, CREATE, WRITE}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}

object Main extends App{
  case class Package(name: String)

  // Requests a package to the API and returns the information about that package
  // Change this code at your convenience
  def apiRequest(packageObj: Package): JsValue = {
    val url = s"https://api.npms.io/v2/package/${packageObj.name}"
    val response = requests.get(url)
    val json: JsValue = Json.parse(response.data.toString)
    json
  }

  implicit val actorSystem: ActorSystem = ActorSystem("PackagesTriage")
  implicit val dispatcher: ExecutionContextExecutor = actorSystem.dispatcher

  val path = Paths.get("src/main/resources/packages.txt.gz")
  val sourceByteString: Source[ByteString, Future[IOResult]] = FileIO.fromPath(path)
  val flowUnzip: Flow[ByteString, ByteString, NotUsed] = Compression.gunzip()
  val flowToPackage: Flow[ByteString, Package, NotUsed] =
    Flow[ByteString].via(Framing.delimiter(ByteString("\n"), 1024, true).map(b => Package(b.utf8String)))





  val PackagesSource:Source[Package,Future[IOResult]] =  sourceByteString.via(flowUnzip).via(flowToPackage)
  val BufferedThrottledPackagesSource = PackagesSource.buffer(25, OverflowStrategy.backpressure).throttle(1, 2.seconds)
  //PackagesSource.to(printSink).run()
  //BufferedThrottledPackagesSource.to(printSink).run()

  val ApiCallFlow: Flow[Package, Information, NotUsed] =
    Flow[Package].map(p => {/*println("got here");*/ Information.fromJson(apiRequest(p))})


  val flowPackages: Graph[FlowShape[Information, Information], NotUsed] = Flow.fromGraph(
    GraphDSL.create() {
      implicit builder =>
        val minstars = 20
        val mintestCoverage = 0.5
        val minReleases = 2
        val minCommitsTop3 = 150

        def filterStars(): Flow[Information,Information,NotUsed] =
          Flow[Information].filter(
            information =>  {
              information.stars() > minstars
            }
        )
        def filterTests(): Flow[Information, Information, NotUsed] =
          Flow[Information].filter(
            i => i.testCoverage() > mintestCoverage)

        def filterReleases(): Flow[Information, Information, NotUsed] =
          Flow[Information].filter(
            i =>  i.releases() > minReleases)
        def filterCommits(): Flow[Information, Information, NotUsed] =
          Flow[Information].filter(
            i =>  i.sumCommitsTop3Contributors() > minCommitsTop3)
        def filterPipeline(): Flow[Information,Information,NotUsed] = filterStars().via(filterTests()).via(filterReleases()).via(filterCommits())
        //Here a balancer would make more sense than a broadcaster as the pipelines are the same.
        //Using a broadcast has the effect that each package will be outputted 3 times from this graph
        val balance = builder.add(Broadcast[Information](3))
        val merge = builder.add(Merge[Information](3))
        val flowOut: Flow[Information, Information, NotUsed] = Flow[Information].map({I => println(s"succeed package ${I.name}");I})
        val toFlow = builder.add(flowOut)

        balance ~> filterPipeline() ~> merge
        balance ~> filterPipeline() ~> merge
        balance ~> filterPipeline() ~> merge ~> toFlow

        FlowShape(balance.in, toFlow.out)
    }
  )

  val flowToByteString: Flow[Information, ByteString, NotUsed] = Flow[Information].map(information => {ByteString(s"${information.name}\n")})

  def clearFile(path:String) = new FileWriter(path).close()
  val pathResultsFile = "src/main/resources/acceptedPackages.txt"

  val sinkToFile: Sink[ByteString, Future[IOResult]] =
    FileIO.toPath(Paths.get(pathResultsFile), Set(CREATE, WRITE, APPEND))

  val finalGraph: RunnableGraph[Future[IOResult]] =
    BufferedThrottledPackagesSource
      .via(ApiCallFlow)
      .via(flowPackages)
      .via(flowToByteString)
      .to(sinkToFile)


  def main() ={
    clearFile(pathResultsFile)
    finalGraph.run()
  }

  main()

}



