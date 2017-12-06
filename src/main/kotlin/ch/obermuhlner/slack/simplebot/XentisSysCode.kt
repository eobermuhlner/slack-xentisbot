package ch.obermuhlner.slack.simplebot

import java.io.BufferedReader
import java.io.FileReader

class XentisSysCode {
	
	val idToSysCode = mutableMapOf<Long, SysCode?>()
	
	fun parse(sysCodeFile: String, sysSubsetFile: String) {
		idToSysCode.clear()
		
		BufferedReader(FileReader(sysCodeFile)).use {
			var lastId = 0L
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
				
				val groupSyscode = idToSysCode[groupId]
				if (groupSyscode != null) {
					groupSyscode.children.add(id)
				} 
				lastId = id
			}
		}
	}
	
	fun isGroupId(id: Long, lastId: Long): Boolean {
		val delta = id - lastId
		return delta < 0 || delta > 1000
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
		message += "    code: `${syscode.code}`\n"
		message += "    name: `${syscode.name}`\n"
		message += "    short translation: _${syscode.germanShort}_ : _${syscode.englishShort}_\n"
		message += "    medium translation: _${syscode.germanMedium}_ : _${syscode.englishMedium}_\n"
		
		message += "    group: ${syscode.groupId.toString(16)}"
		if (groupSyscode != null) {
			message += " `${groupSyscode.name}`"
		}
		message += "\n"
		
		if (syscode.children.size > 0) {
			val maxChildren = 20
			val childrenText = plural(syscode.children.size, "child", "children")
			message += "    ${syscode.children.size} $childrenText found\n"
			for(child in syscode.children.slice(0..Math.min(maxChildren, syscode.children.size-1))) {
				val childSyscode = getSysCode(child)
				message += "        ${child.toString(16)}"
				if (childSyscode != null) {
					message += " `${childSyscode.name}`"
				}
				message += "\n"
			}
			if (syscode.children.size > maxChildren) {
				message += "        ...\n"
			}
		}
		
		return message
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
			val children: MutableList<Long> = mutableListOf())
}