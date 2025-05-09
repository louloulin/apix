"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { useTranslations, useLocale } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Switch } from "@/components/ui/switch"
import { Badge } from "@/components/ui/badge"
import { PlusIcon, PencilIcon, TrashIcon, RefreshCwIcon } from "lucide-react"
import { useToast } from "@/components/ui/use-toast"

// Define the route type
interface Route {
  id: string
  path: string
  target: string
  method: string
  active: boolean
  type: 'llm' | 'vector' | 'other'
}

interface RoutesListProps {
  routes: Route[]
  onToggleStatus: (route: Route) => void
  onDelete: (route: Route) => void
  onEdit: (route: Route) => void
}

export default function RoutesPage() {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('routes')
  const common = useTranslations('common')
  
  const [activeTab, setActiveTab] = useState("all")
  const [searchQuery, setSearchQuery] = useState("")
  
  // Mock data - would come from API in real implementation
  const routes: Route[] = [
    {
      id: "1",
      path: "/v1/completions",
      target: "https://api.openai.com/v1/completions",
      method: "POST",
      active: true,
      type: "llm"
    },
    {
      id: "2",
      path: "/v1/chat/completions",
      target: "https://api.openai.com/v1/chat/completions",
      method: "POST",
      active: true,
      type: "llm"
    },
    {
      id: "3",
      path: "/v1/messages",
      target: "https://api.anthropic.com/v1/messages",
      method: "POST",
      active: true,
      type: "llm"
    },
    {
      id: "4",
      path: "/vectors/search",
      target: "INTERNAL",
      method: "POST",
      active: true,
      type: "vector"
    },
    {
      id: "5",
      path: "/vectors/upsert",
      target: "INTERNAL",
      method: "POST",
      active: true,
      type: "vector"
    }
  ]
  
  // Filter routes based on active tab and search query
  const filteredRoutes = routes.filter(route => {
    const matchesTab = activeTab === "all" || route.type === activeTab
    const matchesSearch = route.path.toLowerCase().includes(searchQuery.toLowerCase()) ||
                         route.target.toLowerCase().includes(searchQuery.toLowerCase())
    return matchesTab && matchesSearch
  })
  
  // Handle route status toggle
  const handleStatusToggle = (route: Route) => {
    toast({
      title: common('success'),
      description: `Route ${route.path} ${route.active ? 'disabled' : 'enabled'} successfully`
    })
  }

  // Handle route deletion
  const handleDelete = (route: Route) => {
    if (confirm(`${t('deleteConfirm', { path: route.path })}`)) {
      toast({
        title: common('success'),
        description: `Route ${route.path} deleted successfully`
      })
    }
  }

  // Handle route edit
  const handleEdit = (route: Route) => {
    router.push(`/${locale}/dashboard/routes/${route.id}/edit`)
  }
  
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground">
            {t('description')}
          </p>
        </div>
        <Button onClick={() => router.push(`/${locale}/dashboard/routes/create`)}>
          <PlusIcon className="mr-2 h-4 w-4" />
          {t('addRoute')}
        </Button>
      </div>
      
      <Card className="mt-6">
        <CardHeader>
          <CardTitle>{t('title')}</CardTitle>
          <CardDescription>
            {t('description')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            <div className="flex items-center space-x-2">
              <div className="relative flex-1 max-w-sm">
                <Input
                  placeholder={`${common('search')}...`}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-8"
                />
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                  />
                </svg>
              </div>
              <Select defaultValue="all">
                <SelectTrigger className="w-[180px]">
                  <SelectValue placeholder={common('status')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t('allStatuses')}</SelectItem>
                  <SelectItem value="active">{common('active')}</SelectItem>
                  <SelectItem value="inactive">{common('inactive')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            
            <Tabs defaultValue="all" className="w-full" onValueChange={setActiveTab}>
              <TabsList>
                <TabsTrigger value="all">{t('allRoutes')}</TabsTrigger>
                <TabsTrigger value="llm">{t('llmRoutes')}</TabsTrigger>
                <TabsTrigger value="vector">{t('vectorRoutes')}</TabsTrigger>
                <TabsTrigger value="other">{t('otherRoutes')}</TabsTrigger>
              </TabsList>
              
              <TabsContent value="all" className="mt-4">
                <RoutesList 
                  routes={filteredRoutes} 
                  onToggleStatus={handleStatusToggle}
                  onDelete={handleDelete}
                  onEdit={handleEdit}
                />
              </TabsContent>
              <TabsContent value="llm" className="mt-4">
                <RoutesList 
                  routes={filteredRoutes} 
                  onToggleStatus={handleStatusToggle}
                  onDelete={handleDelete}
                  onEdit={handleEdit}
                />
              </TabsContent>
              <TabsContent value="vector" className="mt-4">
                <RoutesList 
                  routes={filteredRoutes} 
                  onToggleStatus={handleStatusToggle}
                  onDelete={handleDelete}
                  onEdit={handleEdit}
                />
              </TabsContent>
              <TabsContent value="other" className="mt-4">
                <RoutesList 
                  routes={filteredRoutes} 
                  onToggleStatus={handleStatusToggle}
                  onDelete={handleDelete}
                  onEdit={handleEdit}
                />
              </TabsContent>
            </Tabs>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

function RoutesList({ routes, onToggleStatus, onDelete, onEdit }: RoutesListProps) {
  const t = useTranslations('routes')
  const common = useTranslations('common')
  
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('path')}</TableHead>
          <TableHead>{t('target')}</TableHead>
          <TableHead>{t('method')}</TableHead>
          <TableHead>{common('status')}</TableHead>
          <TableHead className="text-right">{common('actions')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {routes.length === 0 ? (
          <TableRow>
            <TableCell colSpan={5} className="text-center py-8 text-muted-foreground">
              {common('noData')} {t('noRoutes')}
            </TableCell>
          </TableRow>
        ) : (
          routes.map(route => (
            <TableRow key={route.id}>
              <TableCell className="font-medium">{route.path}</TableCell>
              <TableCell>{route.target}</TableCell>
              <TableCell>
                <Badge variant="outline" className="bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300 border-blue-200">
                  {route.method}
                </Badge>
              </TableCell>
              <TableCell>
                <div className="flex items-center space-x-2">
                  <Switch
                    checked={route.active}
                    onCheckedChange={() => onToggleStatus(route)}
                  />
                  <span className={route.active ? "text-green-600 dark:text-green-400" : "text-red-600 dark:text-red-400"}>
                    {route.active ? common('active') : common('inactive')}
                  </span>
                </div>
              </TableCell>
              <TableCell className="text-right">
                <div className="flex justify-end space-x-1">
                  <Button variant="ghost" size="icon" onClick={() => onEdit(route)}>
                    <PencilIcon className="h-4 w-4" />
                    <span className="sr-only">{common('edit')}</span>
                  </Button>
                  <Button variant="ghost" size="icon" onClick={() => onDelete(route)}>
                    <TrashIcon className="h-4 w-4" />
                    <span className="sr-only">{common('delete')}</span>
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  )
}
