package com.contentagent.vibestream

import com.contentagent.vibestream.player.SleepTimer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test

class SleepTimerTest {
 @Test fun expiryFadesAndPausesOnceThenRestoresVolume()=runBlocking {
  var now=1000L;var deadline:Long?=null;var stops=0;var volume=1f
  val ticks=Channel<Unit>(Channel.UNLIMITED)
  val scope=CoroutineScope(Dispatchers.Unconfined+Job())
  val timer=SleepTimer(scope,{now},{ticks.receive()},{deadline=it},{volume=it},{stops++})
  timer.start(15);assertEquals(901000L,deadline);assertEquals(0,stops)
  now=893500L;ticks.send(Unit);yield();assertEquals(0.5f,volume,0.01f)
  now=901000L;ticks.send(Unit);yield()
  assertEquals(1,stops);assertEquals(1f,volume,0f);assertNull(deadline)
  scope.cancel()
 }
 @Test fun replacingAndCancellingNeverStopsPlayback()=runBlocking {
  var now=0L;var deadline:Long?=null;var stops=0
  val ticks=Channel<Unit>(Channel.UNLIMITED)
  val scope=CoroutineScope(Dispatchers.Unconfined+Job())
  val timer=SleepTimer(scope,{now},{ticks.receive()},{deadline=it},{},{stops++})
  for(m in listOf(15,30,45,60)){timer.start(m);assertEquals(m*60000L,deadline)}
  timer.cancel();now=99999999L;ticks.send(Unit);yield()
  assertNull(deadline);assertEquals(0,stops);scope.cancel()
 }
}
