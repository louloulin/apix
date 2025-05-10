"use client";

import { useState, useEffect } from "react";
import { useTranslations } from "next-intl";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { TrafficChart } from "@/components/dashboard/traffic-chart";
import { LlmUsageChart } from "@/components/dashboard/llm-usage-chart";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/use-toast";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  Activity,
  AlertCircle,
  AlertTriangle,
  BarChart,
  Clock,
  RefreshCw,
  Server,
  Zap
} from "lucide-react";
import { dashboardApi, DashboardData } from "@/lib/api-client/dashboard";

export default function DashboardPage() {
  const t = useTranslations("dashboard");
  const common = useTranslations("common");
  const { toast } = useToast();
  
  const [dashboardData, setDashboardData] = useState<DashboardData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [period, setPeriod] = useState<"day" | "week" | "month">("day");
  
  // 加载仪表盘数据
  const loadDashboardData = async () => {
    try {
      setIsLoading(true);
      setError(null);
      
      // 调用 API
      const data = await dashboardApi.getDashboardData(period);
      
      // 更新状态
      setDashboardData(data);
    } catch (err) {
      console.error("Failed to load dashboard data:", err);
      setError(err instanceof Error ? err : new Error("Failed to load dashboard data"));
      toast({
        title: common("error"),
        description: err instanceof Error ? err.message : "Unknown error",
        variant: "destructive"
      });
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  };
  
  // 初始加载和参数变化时重新加载
  useEffect(() => {
    loadDashboardData();
  }, [period]);
  
  // 刷新数据
  const handleRefresh = () => {
    setIsRefreshing(true);
    loadDashboardData();
  };
  
  // 格式化数字
  const formatNumber = (num: number): string => {
    return new Intl.NumberFormat().format(num);
  };
  
  // 格式化百分比
  const formatPercent = (num: number): string => {
    return `${num.toFixed(1)}%`;
  };
  
  // 格式化时间
  const formatTime = (ms: number): string => {
    return `${ms}ms`;
  };
  
  // 格式化金额
  const formatCurrency = (num: number): string => {
    return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(num);
  };
  
  // 格式化令牌数
  const formatTokens = (num: number): string => {
    if (num >= 1000000000) {
      return `${(num / 1000000000).toFixed(1)}B`;
    } else if (num >= 1000000) {
      return `${(num / 1000000).toFixed(1)}M`;
    } else if (num >= 1000) {
      return `${(num / 1000).toFixed(1)}K`;
    } else {
      return num.toString();
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t("title")}</h1>
          <p className="text-muted-foreground">
            {t("welcome")}
          </p>
        </div>
        <Button variant="outline" onClick={handleRefresh} disabled={isRefreshing}>
          <RefreshCw className={`mr-2 h-4 w-4 ${isRefreshing ? "animate-spin" : ""}`} />
          {common("refresh")}
        </Button>
      </div>
      
      {error && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>{common("error")}</AlertTitle>
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}

      <Tabs defaultValue="overview" className="mt-6">
        <TabsList>
          <TabsTrigger value="overview">{t("overview")}</TabsTrigger>
          <TabsTrigger value="analytics">{t("analytics")}</TabsTrigger>
          <TabsTrigger value="llm-usage">{t("llmUsage")}</TabsTrigger>
        </TabsList>
        <TabsContent value="overview" className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t("totalRequests")}
                </CardTitle>
                <Activity className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {isLoading ? (
                    <div className="h-6 w-24 animate-pulse rounded bg-muted"></div>
                  ) : (
                    formatNumber(dashboardData?.stats.totalRequests || 0)
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  +12.5% {t("fromLastMonth")}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t("avgResponseTime")}
                </CardTitle>
                <Clock className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {isLoading ? (
                    <div className="h-6 w-24 animate-pulse rounded bg-muted"></div>
                  ) : (
                    formatTime(dashboardData?.stats.avgResponseTime || 0)
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  -5ms {t("fromLastMonth")}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t("activePlugins")}
                </CardTitle>
                <Server className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {isLoading ? (
                    <div className="h-6 w-24 animate-pulse rounded bg-muted"></div>
                  ) : (
                    dashboardData?.stats.activePlugins || 0
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  +2 {t("fromLastMonth")}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t("errorRate")}
                </CardTitle>
                <AlertTriangle className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {isLoading ? (
                    <div className="h-6 w-24 animate-pulse rounded bg-muted"></div>
                  ) : (
                    formatPercent(dashboardData?.stats.errorRate || 0)
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  -0.04% {t("fromLastWeek")}
                </p>
              </CardContent>
            </Card>
          </div>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-7">
            <Card className="col-span-4">
              <CardHeader>
                <CardTitle>{t("requestTraffic")}</CardTitle>
                <CardDescription>
                  {t("requestVolume")}
                </CardDescription>
              </CardHeader>
              <CardContent className="h-[300px]">
                {isLoading ? (
                  <div className="flex h-full items-center justify-center">
                    <div className="h-8 w-8 animate-spin rounded-full border-b-2 border-primary"></div>
                  </div>
                ) : (
                  <TrafficChart data={dashboardData?.trafficData || []} />
                )}
              </CardContent>
            </Card>
            <Card className="col-span-3">
              <CardHeader>
                <CardTitle>{t("llmUsage")}</CardTitle>
                <CardDescription>
                  {t("distributionByProvider")}
                </CardDescription>
              </CardHeader>
              <CardContent className="h-[300px]">
                {isLoading ? (
                  <div className="flex h-full items-center justify-center">
                    <div className="h-8 w-8 animate-spin rounded-full border-b-2 border-primary"></div>
                  </div>
                ) : (
                  <LlmUsageChart data={dashboardData?.llmUsageData || []} />
                )}
              </CardContent>
            </Card>
          </div>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>{t("recentEvents")}</CardTitle>
                <CardDescription>
                  {t("lastEvents", { count: 5 })}
                </CardDescription>
              </CardHeader>
              <CardContent>
                {isLoading ? (
                  <div className="space-y-2">
                    {[1, 2, 3, 4, 5].map((i) => (
                      <div key={i} className="flex items-center gap-2">
                        <div className="h-2 w-2 rounded-full bg-muted"></div>
                        <div className="h-4 w-24 animate-pulse rounded bg-muted"></div>
                        <div className="h-4 w-16 ml-auto animate-pulse rounded bg-muted"></div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <ul className="space-y-2">
                    {(dashboardData?.events || []).map((event) => {
                      let colorClass = "bg-blue-500";
                      if (event.type === "warning") colorClass = "bg-yellow-500";
                      if (event.type === "error") colorClass = "bg-red-500";
                      if (event.type === "success") colorClass = "bg-green-500";
                      
                      return (
                        <li key={event.id} className="flex items-center gap-2">
                          <span className={`flex h-2 w-2 rounded-full ${colorClass}`}></span>
                          <span className="font-medium">{event.title}</span>
                          <span className="text-sm text-muted-foreground ml-auto">{event.time}</span>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>{t("activeRoutes")}</CardTitle>
                <CardDescription>
                  {t("topActiveRoutes", { count: 5 })}
                </CardDescription>
              </CardHeader>
              <CardContent>
                {isLoading ? (
                  <div className="space-y-2">
                    {[1, 2, 3, 4, 5].map((i) => (
                      <div key={i} className="flex items-center justify-between">
                        <div className="h-4 w-24 animate-pulse rounded bg-muted"></div>
                        <div className="h-4 w-20 animate-pulse rounded bg-muted"></div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <ul className="space-y-2">
                    {(dashboardData?.topRoutes || []).map((route) => (
                      <li key={route.id} className="flex items-center justify-between">
                        <span className="font-medium">{route.name}</span>
                        <span className="text-sm">{formatNumber(route.requests)} {t("requests")}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
          </div>
        </TabsContent>
        <TabsContent value="analytics" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>{t("advancedAnalytics")}</CardTitle>
              <CardDescription>
                {t("detailedMetrics")}
              </CardDescription>
            </CardHeader>
            <CardContent className="h-[400px]">
              <div className="flex h-full items-center justify-center rounded-md border border-dashed">
                <div className="text-center">
                  <p className="text-muted-foreground">{t("analyticsComingSoon")}</p>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="llm-usage" className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t("totalTokens")}
                </CardTitle>
                <BarChart className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {isLoading ? (
                    <div className="h-6 w-16 animate-pulse rounded bg-muted"></div>
                  ) : (
                    formatTokens(dashboardData?.llmStats.totalTokens || 0)
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  +15.2% {t("fromLastMonth")}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t("avgTokensPerRequest")}
                </CardTitle>
                <Activity className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {isLoading ? (
                    <div className="h-6 w-16 animate-pulse rounded bg-muted"></div>
                  ) : (
                    formatNumber(dashboardData?.llmStats.avgTokensPerRequest || 0)
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  +3.1% {t("fromLastMonth")}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t("mostUsedModel")}
                </CardTitle>
                <Zap className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {isLoading ? (
                    <div className="h-6 w-16 animate-pulse rounded bg-muted"></div>
                  ) : (
                    dashboardData?.llmStats.mostUsedModel || "-"
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  72% {t("ofRequests")}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t("estimatedCost")}
                </CardTitle>
                <AlertTriangle className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {isLoading ? (
                    <div className="h-6 w-16 animate-pulse rounded bg-muted"></div>
                  ) : (
                    formatCurrency(dashboardData?.llmStats.estimatedCost || 0)
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  +8.3% {t("fromLastMonth")}
                </p>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>{t("llmProviderUsage")}</CardTitle>
              <CardDescription>
                {t("requestDistribution")}
              </CardDescription>
            </CardHeader>
            <CardContent className="h-[360px]">
              <LlmUsageChart data={dashboardData?.llmUsageData || []} />
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
