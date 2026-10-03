package com.grandl.rankwidget
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
class MainActivity:AppCompatActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main);schedule();render();findViewById<Button>(R.id.refresh).setOnClickListener{refresh()}}
 private fun schedule(){val req=PeriodicWorkRequestBuilder<RankWorker>(30,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();WorkManager.getInstance(this).enqueueUniquePeriodicWork("rank30m",ExistingPeriodicWorkPolicy.UPDATE,req)}
 private fun refresh(){findViewById<TextView>(R.id.status).text="OP.GG'den güncel Solo/Duo verisi alınıyor…";lifecycleScope.launch{try{val data=withContext(Dispatchers.IO){RankRepository.fetch(this@MainActivity)};RankRepository.saveAndDailyDelta(this@MainActivity,data);withContext(Dispatchers.IO){RankRepository.cacheEmblem(this@MainActivity,data.tier)};RankWidgetProvider.updateAll(this@MainActivity);render();findViewById<TextView>(R.id.status).text="Güncellendi • Solo/Duo • yaklaşık 30 dakikada bir otomatik"}catch(e:Exception){findViewById<TextView>(R.id.status).text="Güncelleme başarısız: ${e.message}"}}}
 private fun render(){val p=getSharedPreferences("rank",MODE_PRIVATE);val tier=p.getString("tier","Platinum")!!;findViewById<AuraView>(R.id.aura).setTier(tier);findViewById<TextView>(R.id.player).text="${RankRepository.DISPLAY_NAME}  •  ${RankRepository.REGION}";findViewById<TextView>(R.id.rank).text=if(p.contains("tier"))"$tier ${p.getString("div","")}" else "grandl#wave";findViewById<TextView>(R.id.lp).text=if(p.contains("lp"))"${p.getInt("lp",0)} LP" else "Yenile'ye bas";findViewById<TextView>(R.id.record).text=if(p.contains("wins"))"${p.getInt("wins",0)}W  •  ${p.getInt("losses",0)}L  •  %${p.getInt("wr",0)} WR" else "Solo/Duo";val d=p.getInt("delta",0);findViewById<TextView>(R.id.delta).text=if(p.contains("delta"))"Bugün: ${if(d>=0) "+" else ""}$d LP" else "";findViewById<TextView>(R.id.status).text="Solo/Duo • yaklaşık 30 dakikada bir otomatik yenilenir";lifecycleScope.launch{val bmp=withContext(Dispatchers.IO){RankRepository.cachedEmblem(this@MainActivity,tier)};findViewById<ImageView>(R.id.badge).apply{setImageDrawable(null);bmp?.let{setImageBitmap(it)}}}}
}
