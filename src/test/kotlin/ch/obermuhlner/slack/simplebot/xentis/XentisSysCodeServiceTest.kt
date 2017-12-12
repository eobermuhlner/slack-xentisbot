package ch.obermuhlner.slack.simplebot.xentis

import ch.obermuhlner.slack.simplebot.TranslationService.Translation
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Before
import java.io.StringReader

class XentisSysCodeServiceTest {

    private val service = XentisSysCodeService()

    @Before
    fun setupSysCodes() {
        service.parseSysCodes(StringReader("""
            |16384;4000;SKAD;C_GrpSkadenz;Skadenz;Syscodegruppe Skadenz;SysPeriod;Syscode group periodicity
            |16385;4001;SKAD_SEL;C_GrpSkadenz_Selection;Skadenz;Skadenz;Period;Period
            |67108865;4000001;0001;C_Skadenz_Taeglich;Tägl;Täglich;Dly;Daily
            |67108866;4000002;0007;C_Skadenz_Woechentlich;Wöchtl;Wöchentlich;Wkly;Weekly
            |67108867;4000003;0XXX;C_Skadenz_DontUseTranslation;KOMISCH;KOMISCH;STRANGE;STRANGE
            """.trimMargin()))

        service.parseSysSubsets(StringReader("""
            |C_GrpSkadenz_Selection;C_Skadenz_Taeglich;0;1
            |C_GrpSkadenz_Selection;C_Skadenz_Woechentlich;1;0
            """.trimMargin()))
    }

    @Test
    fun testSysCodes() {
        val syscodeSysPeriod = service.getSysCode(0x1051_0000_0000_4000L)
        assertEquals(0x1051_0000_0000_4000L, syscodeSysPeriod?.id)
        assertEquals(0x1051_0000_0000_4000L, syscodeSysPeriod?.groupId)
        assertEquals("SKAD", syscodeSysPeriod?.code)
        assertEquals("C_GrpSkadenz", syscodeSysPeriod?.name)
        assertEquals("Skadenz", syscodeSysPeriod?.germanShort)
        assertEquals("Syscodegruppe Skadenz", syscodeSysPeriod?.germanMedium)
        assertEquals("SysPeriod", syscodeSysPeriod?.englishShort)
        assertEquals("Syscode group periodicity", syscodeSysPeriod?.englishMedium)
        assertEquals(5, syscodeSysPeriod?.children?.size)
        assertEquals(true, syscodeSysPeriod?.children?.contains(0x1051_0000_0000_4000L))
        assertEquals(true, syscodeSysPeriod?.children?.contains(0x1051_0000_0000_4001L))
        assertEquals(true, syscodeSysPeriod?.children?.contains(0x1051_0000_0400_0001L))
        assertEquals(true, syscodeSysPeriod?.children?.contains(0x1051_0000_0400_0002L))
        assertEquals(true, syscodeSysPeriod?.children?.contains(0x1051_0000_0400_0003L))

        val syscodeDaily = service.getSysCode(0x1051_0000_0400_0001L)
        assertEquals(0x1051_0000_0400_0001L, syscodeDaily?.id)
        assertEquals(0x1051_0000_0000_4000L, syscodeDaily?.groupId)
        assertEquals("C_Skadenz_Taeglich", syscodeDaily?.name)

        val syscodeWeekly = service.getSysCode(0x1051_0000_0400_0002L)
        assertEquals(0x1051_0000_0400_0002L, syscodeWeekly?.id)
        assertEquals(0x1051_0000_0000_4000L, syscodeWeekly?.groupId)
        assertEquals("C_Skadenz_Woechentlich", syscodeWeekly?.name)
    }

    @Test
    fun testSysSubsets() {
        val syscodeSysPeriodGrp = service.getSysCode(0x1051_0000_0000_4000L)
        assertEquals(0, syscodeSysPeriodGrp?.subsetEntries?.size)

        val syscodePeriodGrp = service.getSysCode(0x1051_0000_0000_4001L)
        assertEquals(2, syscodePeriodGrp?.subsetEntries?.size)

        val subsetEntryDaily = syscodePeriodGrp?.subsetEntries?.get(0)
        assertEquals(0x1051_0000_0400_0001L, subsetEntryDaily?.id)
        assertEquals(0, subsetEntryDaily?.sortNumber)
        assertEquals(true, subsetEntryDaily?.defaultEntry)

        val subsetEntryWeekly = syscodePeriodGrp?.subsetEntries?.get(1)
        assertEquals(0x1051_0000_0400_0002L, subsetEntryWeekly?.id)
        assertEquals(1, subsetEntryWeekly?.sortNumber)
        assertEquals(false, subsetEntryWeekly?.defaultEntry)
    }

    @Test
    fun testFindSysCodes() {
        assertEquals(
                listOf(service.getSysCode(0x1051_0000_0400_0001L)),
                service.findSysCodes("Taeglich"))
        assertEquals(
                listOf(service.getSysCode(0x1051_0000_0400_0001L)),
                service.findSysCodes("TAEGLICH"))

        assertEquals(
                listOf(
                        service.getSysCode(0x1051_0000_0400_0001L),
                        service.getSysCode(0x1051_0000_0400_0002L),
                        service.getSysCode(0x1051_0000_0400_0003L)),
                service.findSysCodes("C_Skadenz"))
    }

    @Test
    fun testToMessage_with_group_members() {
        val actualMessage = service.toMessage(service.getSysCode(0x1051_0000_0000_4000L)!!)
        val expectedMessage = """
            |Syscode 1051000000004000 = decimal 1175720977720426496
            |    code: `SKAD`
            |    name: `C_GrpSkadenz`
            |    short translation: _Skadenz_ : _SysPeriod_
            |    medium translation: _Syscodegruppe Skadenz_ : _Syscode group periodicity_
            |    group: 1051000000004000 `C_GrpSkadenz`
            |    5 group members found
            |        1051000000004000 `C_GrpSkadenz`
		    |        1051000000004001 `C_GrpSkadenz_Selection`
            |        1051000004000001 `C_Skadenz_Taeglich`
            |        1051000004000002 `C_Skadenz_Woechentlich`
            |        1051000004000003 `C_Skadenz_DontUseTranslation`
            |
            """.trimMargin().replace("    ", "\t")
        assertEquals(expectedMessage, actualMessage)
    }

    @Test
    fun testToMessage_with_subset_entries() {
        val actualMessage = service.toMessage(service.getSysCode(0x1051_0000_0000_4001L)!!)
        val expectedMessage = """
            |Syscode 1051000000004001 = decimal 1175720977720426497
            |    code: `SKAD_SEL`
            |    name: `C_GrpSkadenz_Selection`
            |    short translation: _Skadenz_ : _Period_
            |    medium translation: _Skadenz_ : _Period_
            |    group: 1051000000004000 `C_GrpSkadenz`
            |    2 subset entries found
            |        1051000004000001 `C_Skadenz_Taeglich`
            |        1051000004000002 `C_Skadenz_Woechentlich`
            |
            """.trimMargin().replace("    ", "\t")
        assertEquals(expectedMessage, actualMessage)
    }

    @Test
    fun testTranslations() {
        // Note: short translations of syscodes are not added
        // Note: all-uppercase translations are not added
        assertEquals(4, service.translations.size)
        service.translations.contains(Translation(english = "Syscode group periodicity", german = "Syscodegruppe Skadenz"))
        service.translations.contains(Translation(english = "Period", german = "Skadenz"))
        service.translations.contains(Translation(english = "Daily", german = "Täglich"))
        service.translations.contains(Translation(english = "Weekly", german = "Wöchentlich"))
    }
}
