// SPDX-License-Identifier: AGPL-3.0-or-later
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const zlib=require('node:zlib');
const html=fs.readFileSync(path.join(__dirname,'../server/public/ccmap/index.html'),'utf8');
const core=html.match(/<script id="ccmap-core">([\s\S]*?)<\/script>/)[1];
const sandbox={module:{exports:{}},Blob,Response,CompressionStream,DecompressionStream};
vm.runInNewContext(core,sandbox,{filename:'ccmap-core.js'});
const api=sandbox.module.exports;
const json=value=>JSON.parse(JSON.stringify(value));
const fixture=()=>({format:'ccmap-json',version:1,name:'room:夹层 😀',fingerprint:'prefix\0𠮷后',anchor:{baseCore:123,rotationCore:-456,distance:3},runs:[
 {x:-2147483648,y:2147483647,z:-2147483648,length:1,material:'minecraft:air',originallyAir:null},
 {x:1,y:70,z:-2,length:5,material:'minecraft:stone',originallyAir:false},
 {x:2,y:71,z:3,length:1,material:'minecraft:water[level=0]',originallyAir:null},
 {x:2147483647,y:-2147483648,z:2147483647,length:1,material:'minecraft:chest[facing=north,type=single,waterlogged=false]',originallyAir:true}
]});
async function bytes(blob){return new Uint8Array(await blob.arrayBuffer());}
async function decode(doc){return api.decode(await bytes(api.encode(doc)));}
test('round trips extreme coordinates, modified UTF, all origin states and anchors',async()=>{
 const doc=api.fromJSON(fixture()),restored=await decode(doc);
 assert.equal(restored.name,fixture().name);assert.equal(restored.fingerprint,fixture().fingerprint);assert.equal(restored.count,8);assert.deepEqual(json(restored.anchor),fixture().anchor);
 assert.deepEqual(json(api.query(restored)),json(api.query(doc)));
 const exported=JSON.parse(await api.toJSONBlob(restored).text());assert.deepEqual(exported,fixture());
});
test('reads real gzip, rejects corruption and produces gzip readable by other tools',async()=>{
 const doc=api.fromJSON(fixture()),raw=await bytes(api.encode(doc));
 const compressed=zlib.gzipSync(raw);assert.equal(api.decode(await bytes(await api.gunzip(new Blob([compressed])))).count,8);
 const produced=await bytes(await api.gzip(api.encode(doc)));assert.deepEqual(zlib.gunzipSync(produced),Buffer.from(raw));
 const corrupt=Buffer.from(compressed);corrupt[corrupt.length-8]^=1;await assert.rejects(api.gunzip(new Blob([corrupt])));
 await assert.rejects(api.gunzip(new Blob([compressed.subarray(0,compressed.length-4)])));
});
test('million-block runs stay compressed in memory and pagination seeks inside long runs',()=>{
 const root=fixture();root.runs=[{x:-3,y:70,z:-500000,length:1000000,material:'minecraft:stone',originallyAir:false}];const doc=api.fromJSON(root);
 assert.equal(doc.runs.length,6);assert.equal(api.describe(doc).count,1000000);assert.deepEqual(json(api.describe(doc).min),[-3,70,-500000]);assert.deepEqual(json(api.describe(doc).max),[-3,70,499999]);
 const last=api.query(doc,{},9999);assert.equal(last.total,1000000);assert.equal(last.rows.length,100);assert.equal(last.rows[0].z,499900);assert.equal(last.rows[99].z,499999);
 const exact=api.query(doc,{x:-3,y:70,z:12345,text:'STONE'});assert.equal(exact.total,1);assert.equal(exact.rows[0].z,12345);assert.equal(api.query(doc,{x:4}).total,0);
});
test('preserves empty layouts and original unknown states during JSON editing',async()=>{
 const root=fixture();root.runs=[];let doc=api.fromJSON(root);assert.equal((await decode(doc)).count,0);assert.equal(api.describe(doc).min,null);assert.equal(JSON.parse(await api.toJSONBlob(doc).text()).runs.length,0);
 doc=api.fromJSON(fixture());const edited=JSON.parse(await api.toJSONBlob(doc).text());edited.runs[1].material='minecraft:dark_oak_planks';const result=await decode(api.fromJSON(edited));assert.equal(api.query(result,{text:'dark_oak'}).total,5);assert.equal(api.query(result,{text:'water'}).rows[0].flag,0);
});
test('sorts edited runs and rejects overlapping coordinates, invalid states and unsafe values',()=>{
 const root=fixture();root.runs.reverse();assert.equal(api.fromJSON(root).count,8);
 for(const change of [r=>r.runs.push({...r.runs[1],z:0}),r=>r.runs[1].length=0,r=>r.runs[1].length=-1,r=>r.runs[1].x=2147483648,r=>r.runs[1].x=1.5,r=>r.runs[1].z=2147483647,r=>r.runs[1].material='../escape',r=>r.runs[1].material='minecraft:stone\n',r=>r.runs[1].originallyAir='false',r=>r.anchor.distance=33,r=>r.name='',r=>r.version=2]){const changed=fixture();change(changed);assert.throws(()=>api.fromJSON(changed));}
 assert.throws(()=>api.fromJSON({rooms:{}}));
});
test('rejects bad magic, versions, truncated bodies, duplicate runs and trailing bytes',async()=>{
 const raw=await bytes(api.encode(api.fromJSON(fixture())));
 assert.throws(()=>api.decode(raw.subarray(0,raw.length-1)));const badMagic=raw.slice();badMagic[0]=0;assert.throws(()=>api.decode(badMagic));const future=raw.slice();future[7]=2;assert.throws(()=>api.decode(future));
 const extra=new Uint8Array(raw.length+1);extra.set(raw);assert.throws(()=>api.decode(extra));
});
test('JSON material distribution and coordinate filters match expanded reference data',()=>{
 let seed=1234;const random=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed;};const root=fixture();root.runs=[];const expanded=[];
 for(let i=0;i<500;i++){const row={x:i%25-12,y:Math.floor(i/25)-10,z:-30,length:random()%21+1,material:random()%2?'minecraft:stone':'minecraft:glass',originallyAir:[null,false,true][random()%3]};root.runs.push(row);for(let n=0;n<row.length;n++)expanded.push({x:row.x,y:row.y,z:row.z+n,material:row.material,flag:row.originallyAir==null?0:row.originallyAir?2:1});}
 expanded.sort((a,b)=>a.x-b.x||a.y-b.y||a.z-b.z);const doc=api.fromJSON(root);assert.equal(api.describe(doc).count,expanded.length);
 for(const filters of [{},{x:0},{y:-8},{z:-25},{x:3,y:1,z:-20},{text:'glass'}]){const expected=expanded.filter(row=>(filters.x==null||row.x===filters.x)&&(filters.y==null||row.y===filters.y)&&(filters.z==null||row.z===filters.z)&&row.material.includes(filters.text||''));for(const page of [0,1,3]){const result=api.query(doc,filters,page);assert.equal(result.total,expected.length);assert.deepEqual(json(result.rows),expected.slice(page*100,page*100+100));}}
});
test('single-file app has no external scripts or uploads and builds actual worker code',()=>{
 assert(!/<script[^>]+src=/i.test(html));assert(!/fetch\s*\(/.test(html));assert(html.includes("connect-src 'none'"));const app=html.match(/<script>\s*([\s\S]*?)<\/script>/)[1];new vm.Script(app);
 const workerMain=app.slice(app.indexOf('function workerMain()'),app.indexOf('const $='));new vm.Script(core+'\n'+workerMain+'\nworkerMain();');
});
test('Java codec interoperability, JSON editing and Java-readable round trip',{skip:!process.env.CCMAP_JAVA_FIXTURE},async()=>{
 const raw=zlib.gunzipSync(fs.readFileSync(process.env.CCMAP_JAVA_FIXTURE)),doc=api.decode(raw);
 assert.deepEqual(JSON.parse(await api.toJSONBlob(doc).text()),fixture());
 const edited=JSON.parse(await api.toJSONBlob(doc).text());edited.runs[1].material='minecraft:dark_oak_planks';
 const output=await bytes(await api.gzip(api.encode(api.fromJSON(edited))));fs.writeFileSync(process.env.CCMAP_NODE_RESULT,output);
});
