package ch.obermuhlner.slack.simplebot

import java.io.BufferedReader
import java.io.FileReader

class XentisSysCode {
	
	val idToSysCode = mutableMapOf<Long, SysCode?>()
	
	fun parse(sysCodeFile: String, sysSubsetFile: String) {
		BufferedReader(FileReader(sysCodeFile)).use {
			for(line in it.readLines()) {
				val fields = line.split(";")
				val syscode = SysCode(
						id=fields[0].toLong() + 0x1051_0000_0000_0000L,
						code=fields[2],
						name=fields[3],
						germanShort=fields[4],
						germanLong=fields[5],
						englishShort=fields[6],
						englishLong=fields[7])
				println(syscode)
				idToSysCode[syscode.id] = syscode
			}
		}
	}
	
	fun getSysCode(id: Long): SysCode? {
		return idToSysCode[id]
	}
	
	fun findSysCodes(text: String): List<SysCode> {
		val result = mutableListOf<SysCode>()
		
		for(syscode in idToSysCode.values) {
			if (syscode != null) {
				if (syscode.code.equals(text) || syscode.name.equals(text)) {
					result.add(syscode)
				}
			}
		}
		
		return result
	}
	
	data class SysCode(
			val id: Long,
			val code: String,
			val name: String,
			val germanShort: String,
			val germanLong: String,
			val englishShort: String,
			val englishLong: String) {
		
		fun toMessage(): String {
			var message = "Syscode ${id.toString(16)} = decimal $id\n"
			
			message += "    code: $code\n"
			message += "    name: $name\n"
			message += "    short translation: $germanShort : $englishShort\n"
			message += "    long translation: $germanLong : $englishLong\n"
			
			return message
		}
	}
}