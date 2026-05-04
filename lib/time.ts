import { fromZonedTime, toZonedTime, formatInTimeZone } from "date-fns-tz";
export const dayRangeUtc=(date:string,tz:string)=>({start:fromZonedTime(`${date}T00:00:00`,tz),end:fromZonedTime(`${date}T23:59:59.999`,tz)});
export const hourLabel=(d:Date,tz:string)=>formatInTimeZone(d,tz,"HH:mm");
export const dayKey=(d:Date,tz:string)=>formatInTimeZone(d,tz,"yyyy-MM-dd");
export const hourKey=(d:Date,tz:string)=>formatInTimeZone(d,tz,"yyyy-MM-dd HH:00");
export const floorHour=(d:Date)=>new Date(Math.floor(d.getTime()/3600000)*3600000);
export const toZone=toZonedTime;
