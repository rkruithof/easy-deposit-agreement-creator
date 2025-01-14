/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.agreement.internal

import java.io.File
import java.{ util => ju }

import nl.knaw.dans.common.lang.dataset.AccessCategory
import nl.knaw.dans.easy.agreement.{ FileAccessRight, UnitSpec }
import nl.knaw.dans.pf.language.emd.Term.{ Name, Namespace }
import nl.knaw.dans.pf.language.emd._
import nl.knaw.dans.pf.language.emd.types._
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll }

import scala.collection.JavaConverters._
import scala.util.Success

class PlaceholderMapperSpec extends UnitSpec with MockFactory with BeforeAndAfter with BeforeAndAfterAll {

  trait MockEasyMetadata extends EasyMetadata {
    def toString(x: String, y: Name): String = ""

    def toString(x: String, y: Term): String = ""

    def toString(x: String, y: MDContainer): String = ""

    def toString(x: String): String = ""
  }

  private val emd = mock[MockEasyMetadata]
  private val ident = mock[EmdIdentifier]
  private val date = mock[EmdDate]
  private val rights = mock[EmdRights]

  implicit val parameters: Parameters = Parameters(
    templateResourceDir = new File(testDir, "placeholdermapper"),
    datasetID = null,
    isSample = false,
    fedora = null,
    ldap = null,
  )

  before {
    new File(getClass.getResource("/placeholdermapper/").toURI).copyDir(parameters.templateResourceDir)
  }

  after {
    parameters.templateResourceDir.deleteDirectory()
  }

  override def afterAll: Unit = testDir.getParentFile.deleteDirectory()

  def testInstance: PlaceholderMapper = {
    new PlaceholderMapper(new File(parameters.templateResourceDir, "MetadataTestTerms.properties"))
  }

  def metadataItemMock(s: String): MetadataItem = {
    new MetadataItem {
      def getSchemeId = throw new NotImplementedError()

      def isComplete = throw new NotImplementedError()

      override def toString: String = s
    }
  }

  "header" should "yield a map of the DOI, date and title" in {
    val dates = ju.Arrays.asList(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))

    emd.getEmdIdentifier _ expects() returning ident
    ident.getDansManagedDoi _ expects() returning "12.3456/dans-ab7-cdef"
    emd.getEmdDate _ expects() returning date
    date.getEasDateSubmitted _ expects() returning dates
    emd.getPreferredTitle _ expects() returning "my preferred title"

    inside(testInstance.header(emd)) {
      case Success(map) => map should {
        have size 5 and contain allOf(
          (IsSample, false),
          (DansManagedDoi, "12.3456/dans-ab7-cdef"),
          (DansManagedEncodedDoi, "12.3456%2Fdans-ab7-cdef"),
          (DateSubmitted, "1992-07-30"),
          (Title, "my preferred title"))
      }
    }
  }

  it should "yield a map with default values if the actual values are null" in {
    emd.getEmdIdentifier _ expects() returning ident
    ident.getDansManagedDoi _ expects() returning null
    emd.getEmdDate _ expects() returning date
    date.getEasDateSubmitted _ expects() returning ju.Collections.emptyList()
    emd.getPreferredTitle _ expects() returning "my preferred title"

    inside(testInstance.header(emd)) {
      case Success(map) => map should {
        have size 5 and contain allOf(
          (IsSample, false),
          (DansManagedDoi, ""),
          (DansManagedEncodedDoi, ""),
          (DateSubmitted, new IsoDate().toString),
          (Title, "my preferred title"))
      }
    }
  }

  "sampleHeader" should "yield a map of the date and title" in {
    implicit val parameters: Parameters = Parameters(
      templateResourceDir = new File(testDir, "placeholdermapper"),
      datasetID = null,
      isSample = true,
      fedora = null,
      ldap = null,
    )
    val dates = ju.Arrays.asList(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))

    emd.getEmdIdentifier _ expects() never()
    ident.getDansManagedDoi _ expects() never()
    emd.getEmdDate _ expects() returning date
    date.getEasDateSubmitted _ expects() returning dates
    emd.getPreferredTitle _ expects() returning "my preferred title"

    inside(testInstance.sampleHeader(emd)) {
      case Success(map) => map should {
        have size 3 and contain allOf(
          (IsSample, true),
          (DateSubmitted, "1992-07-30"),
          (Title, "my preferred title"))
      }
    }
  }

  it should "yield a map with default values if the actual values are null" in {
    implicit val parameters: Parameters = Parameters(
      templateResourceDir = new File(testDir, "placeholdermapper"),
      datasetID = null,
      isSample = true,
      fedora = null,
      ldap = null,
    )

    emd.getEmdIdentifier _ expects() never()
    ident.getDansManagedDoi _ expects() never()
    emd.getEmdDate _ expects() returning date
    date.getEasDateSubmitted _ expects() returning ju.Collections.emptyList()
    emd.getPreferredTitle _ expects() returning "my preferred title"

    inside(testInstance.sampleHeader(emd)) {
      case Success(map) => map should {
        have size 3 and contain allOf(
          (IsSample, true),
          (DateSubmitted, new IsoDate().toString),
          (Title, "my preferred title"))
      }
    }
  }

  "footerText" should "return the text in a file without its line endings" in {
    testInstance.footerText(new File(parameters.templateResourceDir, "FooterTextTest.txt")) shouldBe "hello\nworld"
  }

  "getDate" should "return the first IsoDate from the list generated in the function when this list is not empty" in {
    val isoDate1 = new IsoDate()
    val isoDate2 = new IsoDate()

    emd.getEmdDate _ expects() returning date

    val res = testInstance.getDate(emd)(d => {
      assert(d eq date)
      ju.Arrays.asList(isoDate1, isoDate2)
    })

    res.value shouldBe isoDate1
  }

  it should "return no IsoDate when the list is empty" in {
    emd.getEmdDate _ expects() returning date

    val res = testInstance.getDate(emd)(d => {
      assert(d eq date)
      ju.Collections.emptyList()
    })

    res shouldBe empty
  }

  "depositor" should "yield a map with depositor data" in {
    val depositor = EasyUser("name", "org", "addr", "postal", "city", "country", "tel", "mail")

    testInstance.depositor(depositor) should contain theSameElementsAs List(
      DepositorName -> "name",
      DepositorOrganisation -> "org",
      DepositorAddress -> "addr",
      DepositorPostalCode -> "postal",
      DepositorCity -> "city",
      DepositorCountry -> "country",
      DepositorTelephone -> "tel",
      DepositorEmail -> "mail")
  }

  "accessRights" should "map an Open Access category to an OpenAccess keyword" in {
    expectEmdRights(AccessCategory.OPEN_ACCESS)
    testInstance.isOpenAccess(emd) shouldBe true
  }

  it should "map an Anonymous Access category to an OpenAccess keyword" in {
    expectEmdRights(AccessCategory.ANONYMOUS_ACCESS)
    testInstance.isOpenAccess(emd) shouldBe true
  }

  it should "map a Freely Available category to an OpenAccess keyword" in {
    expectEmdRights(AccessCategory.FREELY_AVAILABLE)
    testInstance.isOpenAccess(emd) shouldBe true
  }

  it should "map an Open Access For Registered Users category to an OpenAccessForRegisteredUsers keyword" in {
    expectEmdRights(AccessCategory.OPEN_ACCESS_FOR_REGISTERED_USERS)
    testInstance.isOpenAccess(emd) shouldBe false
  }

  it should "map a Group Access category to an RestrictGroup keyword" in {
    expectEmdRights(AccessCategory.GROUP_ACCESS)
    testInstance.isOpenAccess(emd) shouldBe false
  }

  it should "map a Request Permission category to an RestrictRequest keyword" in {
    expectEmdRights(AccessCategory.REQUEST_PERMISSION)
    testInstance.isOpenAccess(emd) shouldBe false
  }

  it should "map an Access Elsewhere category to an OtherAccess keyword" in {
    expectEmdRights(AccessCategory.ACCESS_ELSEWHERE)
    testInstance.isOpenAccess(emd) shouldBe false
  }

  it should "map a No Access category to an OtherAccess keyword" in {
    expectEmdRights(AccessCategory.NO_ACCESS)
    testInstance.isOpenAccess(emd) shouldBe false
  }

  it should "map a null value to an OpenAccess keyword" in {
    expectEmdRights(accessCategory = null)
    testInstance.isOpenAccess(emd) shouldBe true
  }

  "embargo" should "give the embargo keyword mappings with UnderEmbargo=true when there is an embargo" in {
    val nextYear = new DateTime().plusYears(1)
    val dates = ju.Arrays.asList(new IsoDate(nextYear), new IsoDate("1992-07-30"))

    emd.getEmdDate _ expects() returning date
    date.getEasAvailable _ expects() returning dates

    testInstance.embargo(emd) should contain theSameElementsAs List(
      (UnderEmbargo, true),
      (DateAvailable, nextYear.toString("YYYY-MM-dd")))
  }

  it should "give the embargo keyword mappings with UnderEmbargo=false when there is no embargo" in {
    val dates = ju.Arrays.asList(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))

    emd.getEmdDate _ expects() returning date
    date.getEasAvailable _ expects() returning dates

    testInstance.embargo(emd) should contain theSameElementsAs List(
      (UnderEmbargo, false),
      (DateAvailable, "1992-07-30")
    )
  }

  it should "give the embargo keyword mappings with UnderEmbargo=false when no DateAvailable is available" in {
    emd.getEmdDate _ expects() returning date
    date.getEasAvailable _ expects() returning ju.Collections.emptyList()

    testInstance.embargo(emd) should contain theSameElementsAs List(
      (UnderEmbargo, false),
      (DateAvailable, "")
    )
  }

  private val licenseTerm = new Term(Name.LICENSE, Namespace.DCTERMS)

  private def expectLicenses(values: Seq[String]) = {
    val items: Seq[MetadataItem] = values.map(new BasicString(_))
    emd.getTerm _ expects licenseTerm returning items.asJava
  }

  private val accessRightsTerm = new Term(Name.ACCESSRIGHTS, Namespace.DCTERMS)

  private def expectEmdRights(accessCategory: AccessCategory) = {
    emd.getEmdRights _ expects() returning rights
    rights.getAccessCategory _ expects() returning accessCategory
  }

  private def expectRightsTerms(accessCategory: AccessCategory) = {
    val items: Seq[MetadataItem] = Seq(new BasicString(accessCategory.toString))
    emd.getTerm _ expects accessRightsTerm returning items.asJava
  }

  "formatAccessRights" should "return a String representation of the access category ANONYMOUS_ACCESS" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("ANONYMOUS_ACCESS")) shouldBe "Anonymous"
  }

  it should "return a String representation of the access category OPEN_ACCESS" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("OPEN_ACCESS")) shouldBe "Open Access"
  }

  it should "return a String representation of the access category OPEN_ACCESS_FOR_REGISTERED_USERS" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("OPEN_ACCESS_FOR_REGISTERED_USERS")) shouldBe "Open access for registered users"
  }

  it should "return a String representation of the access category GROUP_ACCESS" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("GROUP_ACCESS")) shouldBe "Restricted - 'archaeology' group"
  }

  it should "return a String representation of the access category REQUEST_PERMISSION" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("REQUEST_PERMISSION")) shouldBe "Restricted - request permission"
  }

  it should "return a String representation of the access category ACCESS_ELSEWHERE" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("ACCESS_ELSEWHERE")) shouldBe "Elsewhere"
  }

  it should "return a String representation of the access category NO_ACCESS" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("NO_ACCESS")) shouldBe "Other"
  }

  it should "return a String representation of the access category FREELY_AVAILABLE" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("FREELY_AVAILABLE")) shouldBe "Open Access"
  }

  it should "return a String representation of an unknown access category" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("test")) shouldBe "test"
  }

  "formatFileAccessRights" should "return a String representation of the file access category ANONYMOUS" in {
    testInstance.formatFileAccessRights(FileAccessRight.ANONYMOUS) shouldBe "Anonymous"
  }

  it should "return a String representation of the file access category KNOWN" in {
    testInstance.formatFileAccessRights(FileAccessRight.KNOWN) shouldBe "Known"
  }

  it should "return a String representation of the file access category RESTRICTED_REQUEST" in {
    testInstance.formatFileAccessRights(FileAccessRight.RESTRICTED_REQUEST) shouldBe "Restricted request"
  }

  it should "return a String representation of the file access category RESTRICTED_GROUP" in {
    testInstance.formatFileAccessRights(FileAccessRight.RESTRICTED_GROUP) shouldBe "Restricted group"
  }

  it should "return a String representation of the file access category NONE" in {
    testInstance.formatFileAccessRights(FileAccessRight.NONE) shouldBe "None"
  }
}
