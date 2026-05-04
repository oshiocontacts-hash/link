import { prisma } from "../lib/prisma";import { subHours } from "date-fns";
async function main(){await prisma.gameSnapshotMetric.deleteMany();await prisma.snapshot.deleteMany();await prisma.game.deleteMany();
const games=Array.from({length:40}).map((_,i)=>({twitchGameId:`${1000+i}`,name:`Game ${i+1}`,boxArtUrl:"https://static-cdn.jtvnw.net/ttv-boxart/509658-120x168.jpg",firstSeenAt:new Date(),lastSeenAt:new Date()}));
const created=await Promise.all(games.map(g=>prisma.game.create({data:g})));
for(let h=23;h>=0;h--){const t=subHours(new Date(),h);const snap=await prisma.snapshot.create({data:{capturedAtUtc:t,capturedHourUtc:new Date(Math.floor(t.getTime()/3600000)*3600000),language:"all",source:"helix_streams",maxPages:10,fetchedStreamCount:1000,uniqueStreamCount:900,aggregatedGameCount:40}});
const metrics=created.map((g,i)=>{const viewer=Math.max(1000,Math.round((42000-i*700)+(Math.sin((23-h+i)/3)*6000)));return {snapshotId:snap.id,gameId:g.id,rank:i+1,viewerCount:viewer,streamCount:Math.round(viewer/80),sampledStreamCount:Math.round(viewer/80),peakStreamViewerCount:Math.round(viewer/5),topStreamersJson:"[]"};}).sort((a,b)=>b.viewerCount-a.viewerCount).map((m,i)=>({...m,rank:i+1}));
await prisma.gameSnapshotMetric.createMany({data:metrics});}
}
main().finally(()=>prisma.$disconnect());
