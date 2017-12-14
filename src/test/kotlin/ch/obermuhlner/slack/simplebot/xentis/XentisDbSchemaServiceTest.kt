package ch.obermuhlner.slack.simplebot.xentis

import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.StringReader

class XentisDbSchemaServiceTest {

    private lateinit var service: XentisDbSchemaService

    @Before
    fun setup() {
        service = XentisDbSchemaService()

        service.parse(StringReader("""
            | <Table name="ADRESSE" id="0002" type="General" blockSize="10" cachingStrategy="KeepRecent" alias="Address">
            |      <Columns>
            |        <Column name="ADRESSE_ID" nullable="false">
            |          <Format oracle="RAW" size="8" xentis="id"/>
            |        </Column>
            |        <Column name="IDENTEXT_TXT" nullable="true">
            |          <Format oracle="VARCHAR2" size="50" xentis="string"/>
            |        </Column>
            |        <Column name="INSTITUT_ID" nullable="true">
            |          <Format oracle="RAW" size="8" xentis="id">
            |            <ForeignKey name="">INSTITUT</ForeignKey>
            |          </Format>
            |        </Column>
            |        <Column name="INAKTIV_DAT" nullable="true">
            |          <Format oracle="DATE" xentis="date"/>
            |        </Column>
            |        <Column name="SYSVERKNART_BIT" nullable="false">
            |          <Format oracle="RAW" size="8" xentis="bitset" bits="VerknartBits"/>
            |        </Column>
            |      </Columns>
            |    </Table>
            """.trimMargin()))
    }

    @Test
    fun test_getTableId() {
        val id = service.getTableId("ADRESSE")
        assertEquals(2L, id)
    }

    @Test
    fun test_getTableName() {
        val name = service.getTableName(2L)
        assertEquals("ADRESSE", name)
    }

    @Test
    fun test_getTableNames() {
        val names = service.getTableNames("ADR")
        assertEquals(listOf("ADRESSE"), names)
    }

    @Test
    fun test_getTable_UKNOWN() {
        val tableNull = service.getTable("UNKNOWN")
        assertEquals(null, tableNull)
    }

    @Test
    fun test_getTable() {
        val tableAdresse = service.getTable("ADRESSE")
        assertEquals(2L, tableAdresse!!.id)
        assertEquals("ADRESSE", tableAdresse.name)
        assertEquals(5, tableAdresse.columns.size)

        val columnAdresseId = tableAdresse.columns[0]
        assertEquals("ADRESSE_ID", columnAdresseId.name)
        assertEquals("RAW", columnAdresseId.oracleType)
        assertEquals(8, columnAdresseId.size)
        assertEquals("id", columnAdresseId.xentisType)

        val columnIdentTxt = tableAdresse.columns[1]
        assertEquals("IDENTEXT_TXT", columnIdentTxt.name)
        assertEquals("VARCHAR2", columnIdentTxt.oracleType)
        assertEquals(50, columnIdentTxt.size)
        assertEquals("string", columnIdentTxt.xentisType)

        val columnInstitutId = tableAdresse.columns[2]
        assertEquals("INSTITUT_ID", columnInstitutId.name)
        assertEquals("RAW", columnInstitutId.oracleType)
        assertEquals(8, columnInstitutId.size)
        assertEquals("id", columnInstitutId.xentisType)
        assertEquals("INSTITUT", columnInstitutId.foreignKey)

        val columnInaktivDat = tableAdresse.columns[3]
        assertEquals("INAKTIV_DAT", columnInaktivDat.name)
        assertEquals("DATE", columnInaktivDat.oracleType)
        assertEquals(0, columnInaktivDat.size)
        assertEquals("date", columnInaktivDat.xentisType)

        val columnVerknartBits = tableAdresse.columns[4]
        assertEquals("SYSVERKNART_BIT", columnVerknartBits.name)
        assertEquals("RAW", columnVerknartBits.oracleType)
        assertEquals(8, columnVerknartBits.size)
        assertEquals("bitset", columnVerknartBits.xentisType)
    }
}