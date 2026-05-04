import { prisma } from "@/lib/prisma";
import { dayRangeUtc, dayKey, hourKey, hourLabel } from "@/lib/time";
export async function getDashboardData({date,timezone,language,topN}:{date:string;timezone:string;language:string;topN:number}){
  const range=dayRangeUtc(date,timezone);
  const snaps=await prisma.snapshot.findMany({where:{capturedAtUtc:{gte:range.start,lte:range.end},language},orderBy:{capturedAtUtc:"asc"},include:{metrics:{include:{game:true}}}});
  const dates=await prisma.snapshot.findMany({select:{capturedAtUtc:true},distinct:["capturedHourUtc"]});
  const langs=await prisma.snapshot.findMany({select:{language:true},distinct:["language"]});
  const hours=snaps.map(s=>({key:hourKey(s.capturedAtUtc,timezone),label:hourLabel(s.capturedAtUtc,timezone),capturedAtUtc:s.capturedAtUtc.toISOString()}));
  const map=new Map<string, any>();
  for(const s of snaps) for(const m of s.metrics){const e=map.get(m.gameId)??{id:m.gameId,twitchGameId:m.game.twitchGameId,name:m.game.name,boxArtUrl:m.game.boxArtUrl,totalViewerCount:0,peakViewerCount:0,dailyRank:999};e.totalViewerCount+=m.viewerCount;e.peakViewerCount=Math.max(e.peakViewerCount,m.viewerCount);e.dailyRank=Math.min(e.dailyRank,m.rank);map.set(m.gameId,e)}
  const games=[...map.values()].sort((a,b)=>b.peakViewerCount-a.peakViewerCount).slice(0,topN);
  const set=new Set(games.map(g=>g.id));
  const metrics:any[]=[];
  for(const s of snaps){const k=hourKey(s.capturedAtUtc,timezone);for(const g of games){const m=s.metrics.find(x=>x.gameId===g.id);metrics.push({gameId:g.id,hourKey:k,rank:m?.rank??null,viewerCount:m?.viewerCount??null,streamCount:m?.streamCount??null,rankDeltaFromPreviousHour:null,viewerDeltaFromPreviousHour:null});}}
  const summary={totalViewerCount:metrics.reduce((a,m)=>a+(m.viewerCount??0),0),peakHourLabel:hours[0]?.label??null,peakHourViewerCount:0,topViewedGameName:games[0]?.name??null,topViewedGameBoxArtUrl:games[0]?.boxArtUrl??null,topStreamCountGameName:games[0]?.name??null,capturedSnapshotCount:snaps.length,gameCount:games.length};
  return {date,timezone,language,topN,hours,games,metrics,summary,availableDates:[...new Set(dates.map(d=>dayKey(d.capturedAtUtc,timezone)))],availableLanguages:langs.map(l=>l.language)};
}
