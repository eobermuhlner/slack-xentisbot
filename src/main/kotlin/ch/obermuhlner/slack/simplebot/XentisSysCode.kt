package ch.obermuhlner.slack.simplebot

import java.io.BufferedReader
import java.io.FileReader

class XentisSysCode {
	
	val idToSysCode = mutableMapOf<Long, SysCode?>()
	val nameToSysCode = mutableMapOf<String, SysCode?>()
	
	fun parse(sysCodeFile: String, sysSubsetFile: String) {
		idToSysCode.clear()
		nameToSysCode.clear()
		
		BufferedReader(FileReader(sysCodeFile)).use {
			var lastId = Long.MAX_VALUE
			var groupId = 0L
			for(line in it.readLines()) {
				val fields = line.split(";")
				val id = fields[0].toLong() + 0x1051_0000_0000_0000L
				if (isGroupId(id, lastId)) {
					groupId = id
				}
				
				val syscode = SysCode(
						id=id,
						groupId=groupId,
						code=fields[2],
						name=fields[3],
						germanShort=fields[4],
						germanMedium=fields[5],
						englishShort=fields[6],
						englishMedium=fields[7])
				idToSysCode[id] = syscode
				nameToSysCode[syscode.name] = syscode
				
				val groupSyscode = idToSysCode[groupId]
				if (groupSyscode != null) {
					groupSyscode.children.add(id)
				} 
				lastId = id
			}
		}
		
		BufferedReader(FileReader(sysSubsetFile)).use {
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
							id=entrySyscode.id,
							sortNumber=sortNumber,
							defaultEntry=defaultEntry)
					subsetSyscode.subsetEntries.add(subsetEntry)
				}
			}			
		}
	}
	
	fun isGroupId(id: Long, lastId: Long): Boolean {
		val delta = id - lastId
		return delta < 0 || (delta >= 0x900 && delta < 0x10000)
	}
	
	fun getSysCode(id: Long): SysCode? {
		return idToSysCode[id]
	}
	
	fun findSysCodes(text: String): List<SysCode> {
		val result = mutableListOf<SysCode>()
		
		for(syscode in idToSysCode.values) {
			if (syscode != null) {
				if (syscode.code.equals(text) || syscode.name.contains(text)) {
					result.add(syscode)
				}
			}
		}
		
		return result
	}

	fun toMessage(syscode: SysCode): String {
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
	
	fun toSysCodeReference(id: Long): String {
		val hexId = id.toString(16)
		val name = getSysCode(id)?.name.orEmpty() 
		return "$hexId `$name`"
	}
	
	data class SysCode(
			val id: Long,
			val groupId: Long,
			val code: String,
			val name: String,
			val germanShort: String,
			val germanMedium: String,
			val englishShort: String,
			val englishMedium: String,
			val children: MutableList<Long> = mutableListOf(),
			val subsetEntries: MutableList<SysSubsetEntry> = mutableListOf())
	
	data class SysSubsetEntry(
			val id: Long,
			val sortNumber: Int,
			val defaultEntry: Boolean)
	
}

fun <T> limitedForLoop(leftSize: Int, rightSize: Int, elements: Collection<T>, block: (T) -> Unit, skipped: (Int) -> Unit): Unit {
	val x = elements.last()
	
	var index = 0
	val n = elements.size
	for (element in elements) {
		if (index < leftSize || index >= n - rightSize) {
			block(element)
		} else {
			if (index == leftSize) {
				skipped(n - leftSize - rightSize)
			}
		}
		index++
	}
}
