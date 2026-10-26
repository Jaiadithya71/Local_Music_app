package com.contentagent.vibestream.data.analysis
import android.content.Context
import com.contentagent.vibestream.data.chapters.SongChapter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
/** Local cues only. Never stores audio, source tokens or fingerprints. */
class CompilationCache(context: Context) {
 private val prefs=context.getSharedPreferences("vibestream_compilation",0)
 var enabled:Boolean
  get()=prefs.getBoolean("enabled",false)
  set(value){prefs.edit().putBoolean("enabled",value).apply()}
 fun get(id:String):List<SongChapter>?=try {
  prefs.getString("v2_content_$id",null)?.let { Gson().fromJson(it,object:TypeToken<List<SongChapter>>(){}.type) }
 }catch(_:Exception){null}
 fun save(id:String,chapters:List<SongChapter>){prefs.edit().putString("v2_content_$id",Gson().toJson(chapters)).apply()}
}
