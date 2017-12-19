package ch.obermuhlner.slack.simplebot.xentis

import ch.obermuhlner.slack.simplebot.DbSchemaService
import ch.obermuhlner.slack.simplebot.SysCodeService
import ch.obermuhlner.slack.simplebot.SysCodeService.SysCode
import ch.obermuhlner.slack.simplebot.SysCodeService.SysSubsetEntry
import ch.obermuhlner.slack.simplebot.TranslationService.Translation
import ch.obermuhlner.slack.simplebot.limitedForLoop
import ch.obermuhlner.slack.simplebot.plural
import java.io.BufferedReader
import java.io.Reader

class XentisSysCodeService : SysCodeService {

	private val SYSCODE_BASE_VALUE = 0x1051_0000_0000_0000L

    private val idToSysCode = mutableMapOf<Long, SysCode?>()
    private val nameToSysCode = mutableMapOf<String, SysCode?>()

    override val translations get() = getAllTranslations()

    override fun parseSysCodes(sysCodeReader: Reader) {
        idToSysCode.clear()
        nameToSysCode.clear()

        BufferedReader(sysCodeReader).use {
            var lastId = Long.MAX_VALUE
            var groupId = 0L
            for (line in it.readLines()) {
                val fields = line.split(";")
                val id = fields[0].toLong() + SYSCODE_BASE_VALUE
                if (isGroupId(id, lastId)) {
                    groupId = id
                }

                val syscode = SysCode(
                        id = id,
                        groupId = groupId,
                        code = fields[2],
                        name = fields[3],
                        germanShort = fields[4],
                        germanMedium = fields[5],
                        englishShort = fields[6],
                        englishMedium = fields[7])
                addSysCode(syscode)

                val groupSyscode = idToSysCode[groupId]
                if (groupSyscode != null) {
                    groupSyscode.children.add(id)
                }
                lastId = id
            }
        }
    }

	private fun addSysCode(syscode: SysCodeService.SysCode) {
		idToSysCode[syscode.id] = syscode
		nameToSysCode[syscode.name] = syscode
	}

	override fun parseSysSubsets(sysSubsetReader: Reader) {
		BufferedReader(sysSubsetReader).use {
			for(line in it.readLines()) {
				val fields = line.split(";")
				val subsetName = fields[0]
				val entryName = fields[1]
				val sortNumber = fields[2].toInt()
				val defaultEntry = fields[3].toInt() != 0
				
				val subsetSyscode = nameToSysCode[subsetName]
				val entrySyscode = nameToSysCode[entryName]
				
				if (subsetSyscode != null && entrySyscode != null) {
					val subsetEntry = SysSubsetEntry(
							id = entrySyscode.id,
							sortNumber = sortNumber,
							defaultEntry = defaultEntry)
					subsetSyscode.subsetEntries.add(subsetEntry)
				}
			}			
		}
	}

	override fun parseDbSchema(dbSchemaService: DbSchemaService) {
		val groupCode = "DBTables"
		val groupSyscode = SysCode(
				SYSCODE_BASE_VALUE,
				SYSCODE_BASE_VALUE,
                groupCode,
				"C_Grp" + groupCode,
                groupCode,
                groupCode,
                groupCode,
                groupCode)
		addSysCode(groupSyscode)

		for (table in dbSchemaService.tables) {
			val code = (table.id + 0x1000).toString(16)
			val shortName = table.id.toString().padStart(4, '0')
			val name = table.alias ?: table.name.toLowerCase().capitalize()
			val syscode = SysCode(
					table.id + SYSCODE_BASE_VALUE,
					SYSCODE_BASE_VALUE,
					code,
					"C_" + name,
					shortName,
					name,
					shortName,
					name)
			groupSyscode.children.add(syscode.id)
			addSysCode(syscode)
		}
	}
	
	private fun isGroupId(id: Long, lastId: Long): Boolean {
		val delta = id - lastId
		return delta < 0 || (delta >= 0x900 && delta < 0x10000)
	}
	
	override fun getSysCode(id: Long): SysCode? {
		return idToSysCode[id]
	}

	override fun findSysCodes(text: String): List<SysCode> {
		val result = mutableListOf<SysCode>()
		
		for(syscode in idToSysCode.values) {
			if (syscode != null) {
				if (syscode.code.equals(text, ignoreCase = true) || syscode.name.contains(text, ignoreCase = true)) {
					result.add(syscode)
				}
			}
		}
		
		return result
	}
	
	private fun getAllTranslations(): Set<Translation> {
		val result: MutableSet<Translation> = mutableSetOf()
		
		for(syscode in idToSysCode.values) {
			if (syscode != null) {
				if (!isAllUppercase(syscode.englishMedium) && !isAllUppercase(syscode.germanMedium)) {
					result.add(Translation(syscode.englishMedium, syscode.germanMedium))
				}
			}
		}

		return result		
	}
	
	private fun isAllUppercase(text: String): Boolean {
		for(c in text) {
			if (c.isLowerCase()) {
				return false
			}
		}
		return true
	}

	override fun toMessage(syscode: SysCode): String {
		val groupSyscode = getSysCode(syscode.groupId)

		var message = "Syscode ${syscode.id.toString(16)} = decimal ${syscode.id}\n"
		message += "\tcode: `${syscode.code}`\n"
		message += "\tname: `${syscode.name}`\n"
		message += "\tshort translation: _${syscode.germanShort}_ : _${syscode.englishShort}_\n"
		message += "\tmedium translation: _${syscode.germanMedium}_ : _${syscode.englishMedium}_\n"
		
		message += "\tgroup: ${syscode.groupId.toString(16)}"
		if (groupSyscode != null) {
			message += " `${groupSyscode.name}`"
		}
		message += "\n"
		
		if (syscode.children.size > 0) {
			val membersText = plural(syscode.children.size, "member", "members")
			message += "\t${syscode.children.size} group $membersText found\n"

			limitedForLoop(10, 10, syscode.children, { element ->
				message += "\t\t${toSysCodeReference(element)}\n"
			}, { skipped ->
				message += "\t\t... _(skipping $skipped ${plural(skipped, "member", "members")})_\n"
			})
		}			

		if (syscode.subsetEntries.size > 0) {
			val entriesText = plural(syscode.subsetEntries.size, "entry", "entries")
			message += "\t${syscode.subsetEntries.size} subset $entriesText found\n"

			limitedForLoop(10, 10, syscode.subsetEntries, { element ->
				message += "\t\t${toSysCodeReference(element.id)}\n"
			}, { skipped ->
				message += "\t\t... _(skipping $skipped ${plural(skipped, "entry", "entries")})_\n"
			})
		}
		
		return message
	}

	private fun toSysCodeReference(id: Long): String {
		val hexId = id.toString(16)
		val name = getSysCode(id)?.name.orEmpty()
		return "$hexId `$name`"
	}
}
