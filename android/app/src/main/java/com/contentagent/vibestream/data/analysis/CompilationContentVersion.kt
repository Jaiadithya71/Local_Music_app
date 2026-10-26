package com.contentagent.vibestream.data.analysis
import java.io.File
import java.security.MessageDigest
/** Local content identity, not path/mtime/size. Remote sources have no durable cache. */
object CompilationContentVersion {
 fun of(source:String,cancelled:()->Boolean={false}):String? {
  if (source.contains("://") && !source.startsWith("file://")) return null
  val file=File(source.removePrefix("file://"));if(!file.isFile)return null
  val digest=MessageDigest.getInstance("SHA-256")
  file.inputStream().use { input ->
   val block=ByteArray(65536)
   while(true){check(!cancelled()){ "Analysis cancelled" };val size=input.read(block);if(size<0)break;digest.update(block,0,size)}
  }
  return digest.digest().joinToString(""){"%02x".format(it)}
 }
}
