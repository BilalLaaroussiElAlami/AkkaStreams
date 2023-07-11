
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, Broadcast, Compression, FileIO, Flow, Framing, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{FlowShape, Graph, IOResult, OverflowStrategy}
import akka.util.ByteString
import akka.{Done, NotUsed}
import play.api.libs.json.{JsArray, JsValue, Json}

import java.nio.file.Paths
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import GraphDSL.Implicits._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer


object Main extends App{
  case class Package(name: String)

  // Requests a package to the API and returns the information about that package
  // Change this code at your convenience
  def apiRequest(packageObj: Package): JsValue = {
   // println(s"Analysing ${packageObj.name}")
    val url = s"https://api.npms.io/v2/package/${packageObj.name}"
    val response = requests.get(url)
    val json: JsValue = Json.parse(response.data.toString)
    json
  }

  implicit val actorSystem: ActorSystem = ActorSystem("Exercise2")
  implicit val dispatcher: ExecutionContextExecutor = actorSystem.dispatcher

  val path = Paths.get("src/main/resources/packages.txt.gz")
  val sourceByteString: Source[ByteString, Future[IOResult]] = FileIO.fromPath(path)
  val flowUnzip: Flow[ByteString, ByteString, NotUsed] = Compression.gunzip()
  val flowToPackage: Flow[ByteString, Package, NotUsed] =
    Flow[ByteString].via(Framing.delimiter(ByteString("\n"), 1024, true).map(b => Package(b.utf8String)))


  val printSink:Sink[Package,Future[Done]] = Sink.foreach(p => {println(s"$p")})
  val printPackagesgraph = sourceByteString.via(flowUnzip).via(flowToPackage).to(printSink)
  // printPackagesgraph.run()

  val PackagesSource:Source[Package,Future[IOResult]] =  sourceByteString.via(flowUnzip).via(flowToPackage)
  val BufferedThrottledPackagesSource = PackagesSource.buffer(25, OverflowStrategy.backpressure).throttle(1, 2.seconds)
  //PackagesSource.to(printSink).run()
  //BufferedThrottledPackagesSource.to(printSink).run()

  val ApiCallFlow: Flow[Package, Information, NotUsed] =
    Flow[Package].map(p => {/*println("got here");*/ Information.fromJson(apiRequest(p))})


  val flowPackages: Graph[FlowShape[Information, Information], NotUsed] = Flow.fromGraph(
    GraphDSL.create() {
      implicit builder =>

        def filterStars(): Flow[Information,Information,NotUsed] =
          Flow[Information].filter(
            information =>  {
              information.stars() > 20 || println(s"discarded package ${information.name} because it has ${information.stars()} stars\n") == ""
            }
        )
        def filterTests(): Flow[Information, Information, NotUsed] =
          Flow[Information].filter(
            i => {i.testCoverage() > 0.5 ||  println(s"discarded package ${i.name} because it has ${i.testCoverage()} coverage\n")  == "" }
        )
        def filterReleases(): Flow[Information, Information, NotUsed] =
          Flow[Information].filter(
            i => { i.releases() > 2 ||  println(s"discarded package ${i.name} because it has ${i.releases()} releases\n")  == ""})
        def filterCommits(): Flow[Information, Information, NotUsed] =
          Flow[Information].filter(
            i => { i.sumCommitsTop3Contributors() > 150 ||  println(s"discarded package ${i.name} because it has ${i.sumCommitsTop3Contributors()} sumcommits\n")  == "" })

        def filterPipeline(): Flow[Information,Information,NotUsed] = filterStars().via(filterTests()).via(filterReleases()).via(filterCommits())

        val balance = builder.add(Balance[Information](3))
        val merge = builder.add(Merge[Information](3))
        val flowOut: Flow[Information, Information, NotUsed] = Flow[Information].map(I => I)
        val toFlow = builder.add(flowOut)

        balance ~> filterPipeline() ~> merge
        balance ~> filterPipeline() ~> merge
        balance ~> filterPipeline() ~> merge ~> toFlow

        FlowShape(balance.in, toFlow.out)
    }
  )

  val informationPrintSink:Sink[Information,Future[Done]] = Sink.foreach(i => println(s"succeed criteria ${i.name}"))

  val collectPackagesSink: Sink[Information, Future[ListBuffer[Information]]] =
    Sink.fold[ListBuffer[Information], Information](ListBuffer.empty[Information])((buffer, info) => {println(s"succeed criteria ${info.name}"); buffer += info})

   val finalGraph: RunnableGraph[Future[IOResult]] =
    BufferedThrottledPackagesSource
      .via(ApiCallFlow)
      .via(flowPackages)
      .to(collectPackagesSink)

  finalGraph.run()

}



