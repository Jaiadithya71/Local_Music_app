import numpy as np,struct,zlib,base64,re
from pathlib import Path
root=Path(__file__).resolve().parents[2]
pcm=np.frombuffer((root/'android/app/src/test/resources/fingerprints/probe.pcm').read_bytes(),'<i2')
window=np.array([float(x) for x in re.findall(r'[\d.]+',(root/'android/app/src/main/java/com/contentagent/vibestream/data/analysis/FingerprintWindow.kt').read_text().split('floatArrayOf(')[1].split(')')[0].replace('f',''))],dtype=np.float32)
ring=np.zeros(2048,np.int16);hist=np.zeros((256,1025),np.float32);spread=hist.copy();peaks=[[],[],[],[]];ri=fi=si=passes=0
for chunk in pcm[:len(pcm)//128*128].reshape(-1,128):
 ring[ri:ri+128]=chunk;ri=(ri+128)&2047
 samples=ring[(np.arange(2048)+ri)&2047].astype(np.float32)*window
 fft=np.fft.rfft(samples);out=np.maximum((fft.real**2+fft.imag**2)/131072,1e-10).astype(np.float32)
 hist[fi]=out;fi=(fi+1)&255;spread[si]=out
 for i in range(1023):spread[si,i]=max(spread[si,i:i+3])
 for former in [1,3,6]:spread[(si-former)&255]=np.maximum(spread[(si-former)&255],spread[si])
 si=(si+1)&255;passes+=1
 if passes<46:continue
 candidate=hist[(fi-46)&255];surround=spread[(si-49)&255]
 for b in range(10,1015):
  v=candidate[b]
  if v<1/64 or v<surround[b-1]:continue
  neighbor=max(surround[b+d] for d in [-10,-7,-4,-3,1,2,5,8])
  if v<=neighbor:continue
  neighbor=max(neighbor,max(spread[(si+d)&255,b-1] for d in [-53,-45,165,172,179,186,193,200,214,221,228,235,242,249]))
  if v<=neighbor:continue
  def mag(v):return np.float32(np.float32(max(np.log(v),np.float32(1/64)))*np.float32(1477.3)+np.float32(6144))
  m=mag(v);before=mag(candidate[b-1]);after=mag(candidate[b+1]);cur=np.float32(m*2-before-after)
  if cur<=0:continue
  freq=b*64+int(np.float32(np.float32(after-before)*np.float32(32)/cur));hz=freq*16000/2/1024/64
  band=0 if 250<=int(hz)<=519 else 1 if 520<=int(hz)<=1449 else 2 if 1450<=int(hz)<=3499 else 3 if 3500<=int(hz)<=5500 else -1
  if band>=0:peaks[band].append((passes-46,int(m),freq))
b=bytearray(struct.pack('<14I',0xcafe2580,0,0,0x94119c00,0,0,0,3<<27,0,0,len(pcm)+3840,(15<<19)+0x40000,0x40000000,0))
for band,items in enumerate(peaks):
 if not items:continue
 body=bytearray();last=0
 for p,m,f in items:
  if p-last>=255:body+=struct.pack('<BI',255,p);last=p
  body+=struct.pack('<BHH',p-last,m,f);last=p
 b+=struct.pack('<II',0x60030040+band,len(body))+body+b'\0'*((-len(body))%4)
struct.pack_into('<I',b,8,len(b)-48);struct.pack_into('<I',b,52,len(b)-48);struct.pack_into('<I',b,4,zlib.crc32(b[8:]))
(root/'android/app/src/test/resources/fingerprints/probe.uri').write_text('data:audio/vnd.shazam.sig;base64,'+base64.b64encode(b).decode())
print('raw reference',len(b),'peaks',[len(p) for p in peaks])
