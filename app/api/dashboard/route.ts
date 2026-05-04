import { NextRequest, NextResponse } from "next/server";import { z } from "zod";import { getDashboardData } from "@/lib/dashboard-query";
const schema=z.object({date:z.string().optional(),language:z.string().default("all"),topN:z.coerce.number().default(20),timezone:z.string().default("Asia/Tokyo")});
export async function GET(req:NextRequest){const q=schema.parse(Object.fromEntries(req.nextUrl.searchParams));const date=q.date??new Date().toISOString().slice(0,10);return NextResponse.json(await getDashboardData({...q,date}));}
