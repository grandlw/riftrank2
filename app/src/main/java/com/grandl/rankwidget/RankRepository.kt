package com.grandl.rankwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.time.LocalDate

// RiftRank v10: deliberately fixed to grandl#wave / TR.
data class RankData(val tier:String,val division:String,val lp:Int,val wins:Int,val losses:Int,val wr:Int)

object RankRepository {
 const val PROFILE_URL="https://op.gg/tr/lol/summoners/tr/grandl-wave"
 const val DISPLAY_NAME="grandl#wave"
 const val REGION="TR"

 private val rankRe=Regex("(Iron|Bronze|Silver|Gold|Platinum|Emerald|Diamond|Master|Grandmaster|Challenger)\\s*([1-4IVX]*)\\s+(\\d+)\\s+LP",RegexOption.IGNORE_CASE)
 private val wlTr=Regex("(\\d+)G\\s+(\\d+)M\\s+Kazanma oranı\\s+(\\d+)%",RegexOption.IGNORE_CASE)
 private val wlEn=Regex("(\\d+)W\\s+(\\d+)L\\s+Win rate\\s+(\\d+)%",RegexOption.IGNORE_CASE)

 fun fetch(c:Context):RankData = fetchFresh()

 private fun fetchFresh():RankData {
  // Cache-buster + desktop UA: request the same current profile document a browser sees.
  val url="$PROFILE_URL?refresh=${System.currentTimeMillis()}"
  val doc=Jsoup.connect(url)
   .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36")
   .referrer("https://op.gg/")
   .header("Accept-Language","tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
   .header("Cache-Control","no-cache, no-store, max-age=0")
   .header("Pragma","no-cache")
   .ignoreHttpErrors(false)
   .timeout(25000)
   .get()

  val text=doc.body().wholeText().replace(Regex("\\s+")," ").trim()

  // OP.GG has navigation and history entries containing the same heading.
  // Inspect every Solo/Duo heading and accept only a nearby block containing BOTH
  // current rank+LP and season W/L. This prevents matching navigation/history.
  val headings=listOf("Dereceli Tek/Çift","Ranked Solo/Duo")
  val candidates=mutableListOf<String>()
  for(h in headings){
   var from=0
   while(true){
    val i=text.indexOf(h,from,ignoreCase=true)
    if(i<0) break
    val end=(i+420).coerceAtMost(text.length)
    candidates += text.substring(i,end)
    from=i+h.length
   }
  }

  for(block in candidates){
   val r=rankRe.find(block)
   val w=wlTr.find(block)?:wlEn.find(block)
   if(r!=null && w!=null) return make(r,w)
  }

  // Defensive fallback: find a W/L record and pair it only with the closest rank
  // immediately before it, instead of blindly taking the first rank on the page.
  val w=wlTr.find(text)?:wlEn.find(text)?:error("Solo/Duo W/L bulunamadı")
  val before=text.substring(0,w.range.first)
  val r=rankRe.findAll(before).lastOrNull()?:error("Solo/Duo rank bulunamadı")
  return make(r,w)
 }

 private fun make(r:MatchResult,w:MatchResult)=RankData(
  r.groupValues[1].lowercase().replaceFirstChar{it.uppercase()},
  norm(r.groupValues[2]),r.groupValues[3].toInt(),
  w.groupValues[1].toInt(),w.groupValues[2].toInt(),w.groupValues[3].toInt()
 )
 private fun norm(s:String)=when(s.uppercase()){"I","1"->"1";"II","2"->"2";"III","3"->"3";"IV","4"->"4";else->s}

 fun emblemUrl(tier:String)="https://opgg-static.akamaized.net/images/medals_new/${tier.lowercase()}.png?image=q_auto:good,f_png,w_288"
 fun fetchEmblem(tier:String):Bitmap?=try{URL(emblemUrl(tier)).openConnection().run{connectTimeout=15000;readTimeout=15000;useCaches=false;getInputStream().use{BitmapFactory.decodeStream(it)}}}catch(_:Exception){null}
 fun cacheEmblem(c:Context,tier:String):Bitmap?{val bmp=fetchEmblem(tier)?:return cachedEmblem(c,tier);return try{val f=File(c.filesDir,"rank_${tier.lowercase()}.png");FileOutputStream(f).use{bmp.compress(Bitmap.CompressFormat.PNG,100,it)};bmp}catch(_:Exception){bmp}}
 fun cachedEmblem(c:Context,tier:String):Bitmap?=try{val f=File(c.filesDir,"rank_${tier.lowercase()}.png");if(f.exists())BitmapFactory.decodeFile(f.absolutePath)else null}catch(_:Exception){null}

 fun saveAndDailyDelta(c:Context,d:RankData):Int{
  val p=c.getSharedPreferences("rank",Context.MODE_PRIVATE);val today=LocalDate.now().toString();val now=score(d)
  if(p.getString("day",null)!=today)p.edit().putString("day",today).putInt("baseScore",now).apply()
  val delta=now-p.getInt("baseScore",now)
  p.edit().putString("tier",d.tier).putString("div",d.division).putInt("lp",d.lp).putInt("wins",d.wins).putInt("losses",d.losses).putInt("wr",d.wr).putInt("delta",delta).putLong("updated",System.currentTimeMillis()).apply();return delta
 }
 private fun score(d:RankData):Int{val tiers=listOf("Iron","Bronze","Silver","Gold","Platinum","Emerald","Diamond");val ti=tiers.indexOfFirst{it.equals(d.tier,true)};if(ti>=0){val div=d.division.toIntOrNull()?.coerceIn(1,4)?:4;return ti*400+(4-div)*100+d.lp};return 10000+when(d.tier.lowercase()){"master"->0;"grandmaster"->2000;"challenger"->4000;else->0}+d.lp}
}
