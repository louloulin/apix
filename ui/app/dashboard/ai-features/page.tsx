"use client"

import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { BarChart3, Database, FileText, ShieldCheck, Combine, GitFork, Cpu, AreaChart, Clock } from "lucide-react"
import Link from "next/link"

export default function AIFeaturesPage() {
  return (
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">AI Features</h1>
            <p className="text-muted-foreground">
              Configure and manage AI gateway capabilities
            </p>
          </div>
        </div>

        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          <Card className="flex flex-col">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>AI Models</CardTitle>
                <Cpu className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardDescription>
                Manage available AI models and routing rules
              </CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                View available AI models, configure model settings, and set up
                intelligent routing rules based on content analysis.
              </p>
            </CardContent>
            <CardFooter className="mt-auto">
              <Link href="/dashboard/ai-features/models" className="w-full">
                <Button className="w-full">Manage Models</Button>
              </Link>
            </CardFooter>
          </Card>

          <Card className="flex flex-col">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>Usage Statistics</CardTitle>
                <BarChart3 className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardDescription>
                Monitor AI usage and token consumption
              </CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Track token usage, request volume, and costs across different models.
                View usage patterns and optimize your AI consumption.
              </p>
            </CardContent>
            <CardFooter className="mt-auto">
              <Link href="/dashboard/ai-features/usage" className="w-full">
                <Button className="w-full">View Statistics</Button>
              </Link>
            </CardFooter>
          </Card>

          <Card className="flex flex-col">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>Response Cache</CardTitle>
                <Clock className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardDescription>
                Manage AI response caching
              </CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Configure caching settings, view cache statistics, and clear cache
                to improve performance and reduce costs.
              </p>
            </CardContent>
            <CardFooter className="mt-auto">
              <Link href="/dashboard/ai-features/cache" className="w-full">
                <Button className="w-full">Manage Cache</Button>
              </Link>
            </CardFooter>
          </Card>

          <Card className="flex flex-col">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>Prompt Templates</CardTitle>
                <FileText className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardDescription>
                Create and manage prompt templates for different use cases
              </CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Standardize prompts across your organization with reusable templates.
                Apply system messages, context, and safety filters automatically.
              </p>
            </CardContent>
            <CardFooter className="mt-auto">
              <Link href="/dashboard/ai-features/prompts" className="w-full">
                <Button className="w-full">Manage Templates</Button>
              </Link>
            </CardFooter>
          </Card>

          <Card className="flex flex-col">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>Vector Databases</CardTitle>
                <Database className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardDescription>
                Configure vector database integrations for RAG workflows
              </CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Connect to Pinecone, Qdrant, Weaviate, and Milvus for similarity search.
                Manage embeddings, namespaces, and vector operations.
              </p>
            </CardContent>
            <CardFooter className="mt-auto">
              <Link href="/dashboard/ai-features/vector-db" className="w-full">
                <Button className="w-full">Manage Vector DBs</Button>
              </Link>
            </CardFooter>
          </Card>

          <Card className="flex flex-col">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>LLM Providers</CardTitle>
                <AreaChart className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardDescription>
                Configure and manage LLM provider integrations
              </CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Set up connections to OpenAI, Anthropic, Google, Azure, and other LLM providers.
                Configure API keys, model selections, and routing strategies.
              </p>
            </CardContent>
            <CardFooter className="mt-auto">
              <Link href="/dashboard/ai-features/llm-providers" className="w-full">
                <Button className="w-full">Manage Providers</Button>
              </Link>
            </CardFooter>
          </Card>

          <Card className="flex flex-col">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>Safety & Moderation</CardTitle>
                <ShieldCheck className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardDescription>
                Configure content moderation and safety settings
              </CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Set up prompt injection prevention, sensitive information detection,
                and content moderation filters for safer AI interactions.
              </p>
            </CardContent>
            <CardFooter className="mt-auto">
              <Link href="/dashboard/ai-features/safety" className="w-full">
                <Button className="w-full" variant="outline">Manage Safety</Button>
              </Link>
            </CardFooter>
          </Card>

          <Card className="flex flex-col">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>Model Aggregation</CardTitle>
                <Combine className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardDescription>
                Configure multi-model aggregation strategies
              </CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Set up strategies for combining responses from multiple LLMs,
                including fastest-response, weighted-ensemble, and chain-of-models.
              </p>
            </CardContent>
            <CardFooter className="mt-auto">
              <Link href="/dashboard/ai-features/aggregation" className="w-full">
                <Button className="w-full" variant="outline">Configure Aggregation</Button>
              </Link>
            </CardFooter>
          </Card>

          <Card className="flex flex-col">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>Semantic Routing</CardTitle>
                <GitFork className="h-5 w-5 text-muted-foreground" />
              </div>
              <CardDescription>
                Configure content-based routing rules
              </CardDescription>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-muted-foreground">
                Set up intelligent routing based on prompt content,
                sending different types of queries to specialized models.
              </p>
            </CardContent>
            <CardFooter className="mt-auto">
              <Link href="/dashboard/ai-features/routing" className="w-full">
                <Button className="w-full" variant="outline">Configure Routing</Button>
              </Link>
            </CardFooter>
          </Card>
        </div>
      </div>
    </DashboardLayout>
  )
}