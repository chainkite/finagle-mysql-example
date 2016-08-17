
import com.twitter.conversions.time._
import com.twitter.finagle.client.DefaultPool
import com.twitter.finagle.exp.Mysql
import com.twitter.finagle.exp.mysql.{Charset, IntValue, LongValue, Parameter}
import com.twitter.finagle.exp.mysql.CanBeParameter._
import com.twitter.util
import com.wix.mysql.EmbeddedMysql._
import com.wix.mysql.config.Charset._
import com.wix.mysql.config.MysqldConfig._
import com.wix.mysql.distribution.Version._
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global


class UnderflowExceptionTest extends FunSpec with Matchers with BeforeAndAfterAll {

  val ddl =
    """
      | CREATE TABLE test(
      |    id bigint,
      |    message text
      | );
    """.stripMargin

  val dml = "insert into test(id, message) values (?, ?);"

  val mysqld = anEmbeddedMysql(aMysqldConfig(v5_7_latest)
    .withCharset(UTF8)
    .withPort(7878)
    .withUser("test", "test")
    .withTimeZone("UTC")
    .build()
  ).addSchema("test", com.wix.mysql.Sources.fromString(ddl)).start()

  val client = Mysql.client
    .withCredentials("test", "test")
    .withDatabase("test")
    .withCharset(Charset.Utf8_general_ci)
    .withRequestTimeout(3.seconds)
    .configured(DefaultPool.Param(
      low = 0, high = 200, idleTime = 1.minute, bufferSize = 0, maxWaiters = Int.MaxValue))
    .newRichClient("127.0.0.1:7878")

  val prepared = client.prepare(dml);

  def count() = client.select[Option[Long]]("select count(1) from test;")(_.values.headOption.flatMap {
    case IntValue(i) => Some(i.toLong)
    case LongValue(l) => Some(l)
    case _ => None
  }).map(_.headOption.flatten.getOrElse(0L))

  override def afterAll() = {
    mysqld.stop
  }

  it("should write") {
    val para = 10000
    // throw `too many connections` exception as well, maybe that's the reason of UnderflowException
    noException should be thrownBy (1 to para).foreach { i =>
      prepared(System.currentTimeMillis(), "test")
    }
    util.Await.result(count(), util.Duration.Top) should equal(para)
  }

  it("should close") {
    noException should be thrownBy util.Await.result(client.close(), util.Duration.Top)
  }

}
