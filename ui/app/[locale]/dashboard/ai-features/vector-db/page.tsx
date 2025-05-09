"use client"

import { useState } from "react"
import { useTranslations } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Switch } from "@/components/ui/switch"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Badge } from "@/components/ui/badge"
import { AlertCircle, Database, Plus, RefreshCw, Trash2, Edit, Play } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Separator } from "@/components/ui/separator"
import { useToast } from "@/components/ui/use-toast"

// Define vector database types
interface VectorDBConfig {
  name: string
  enabled: boolean
  type: "pinecone" | "qdrant" | "weaviate" | "milvus"
  apiKey?: string
  endpoint?: string
  namespace?: string
  dimensions: number
  metric: "cosine" | "euclidean" | "dot"
  indexName?: string
}

export default function VectorDBPage() {
  const t = useTranslations('vectorDB')
  const common = useTranslations('common')
  const { toast } = useToast()
  
  const [activeTab, setActiveTab] = useState("connections")
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<Error | null>(null)
  
  // Mock vector database configurations
  const [vectorDBs, setVectorDBs] = useState<VectorDBConfig[]>([
    {
      name: "Product Knowledge Base",
      enabled: true,
      type: "pinecone",
      apiKey: "**************************",
      endpoint: "https://product-kb-12345.svc.us-west1-gcp.pinecone.io",
      namespace: "products",
      dimensions: 1536,
      metric: "cosine",
      indexName: "product-kb"
    },
    {
      name: "Customer Support FAQ",
      enabled: true,
      type: "qdrant",
      endpoint: "https://qdrant-instance.company.com:6333",
      namespace: "support-faq",
      dimensions: 768,
      metric: "cosine",
      indexName: "support-collection"
    },
    {
      name: "Legal Documents",
      enabled: false,
      type: "weaviate",
      apiKey: "",
      endpoint: "",
      namespace: "legal",
      dimensions: 1536,
      metric: "cosine",
      indexName: "legal-docs"
    }
  ])
  
  // Toggle vector DB status
  const toggleDBStatus = (index: number) => {
    const newVectorDBs = [...vectorDBs]
    newVectorDBs[index].enabled = !newVectorDBs[index].enabled
    setVectorDBs(newVectorDBs)
    
    toast({
      title: newVectorDBs[index].enabled ? t('connectionEnabled') : t('connectionDisabled'),
      description: `${newVectorDBs[index].name} ${newVectorDBs[index].enabled ? t('isNowEnabled') : t('isNowDisabled')}`,
    })
  }
  
  // Delete vector DB
  const deleteVectorDB = (index: number) => {
    if (confirm(t('confirmDelete'))) {
      const newVectorDBs = [...vectorDBs]
      const deletedDB = newVectorDBs.splice(index, 1)[0]
      setVectorDBs(newVectorDBs)
      
      toast({
        title: t('connectionDeleted'),
        description: `${deletedDB.name} ${t('hasBeenDeleted')}`,
      })
    }
  }
  
  // Add new vector DB
  const addVectorDB = (db: VectorDBConfig) => {
    setVectorDBs([...vectorDBs, db])
    
    toast({
      title: t('connectionAdded'),
      description: `${db.name} ${t('hasBeenAdded')}`,
    })
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
        <Dialog>
          <DialogTrigger asChild>
            <Button>
              <Plus className="mr-2 h-4 w-4" />
              {t('addConnection')}
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[550px]">
            <DialogHeader>
              <DialogTitle>{t('addConnectionTitle')}</DialogTitle>
              <DialogDescription>
                {t('addConnectionDescription')}
              </DialogDescription>
            </DialogHeader>
            <div className="grid gap-4 py-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-2">
                  <Label htmlFor="connection-name">{t('connectionName')}</Label>
                  <Input id="connection-name" placeholder={t('connectionNamePlaceholder')} />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="connection-type">{t('connectionType')}</Label>
                  <Select defaultValue="pinecone">
                    <SelectTrigger id="connection-type">
                      <SelectValue placeholder={t('selectConnectionType')} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="pinecone">Pinecone</SelectItem>
                      <SelectItem value="qdrant">Qdrant</SelectItem>
                      <SelectItem value="weaviate">Weaviate</SelectItem>
                      <SelectItem value="milvus">Milvus</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
              
              <div className="grid gap-2">
                <Label htmlFor="api-key">{t('apiKey')}</Label>
                <Input id="api-key" type="password" placeholder="sk-..." />
              </div>
              
              <div className="grid gap-2">
                <Label htmlFor="endpoint">{t('endpoint')}</Label>
                <Input id="endpoint" placeholder="https://..." />
              </div>
              
              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-2">
                  <Label htmlFor="namespace">{t('namespace')}</Label>
                  <Input id="namespace" placeholder={t('namespacePlaceholder')} />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="index-name">{t('indexName')}</Label>
                  <Input id="index-name" placeholder={t('indexNamePlaceholder')} />
                </div>
              </div>
              
              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-2">
                  <Label htmlFor="dimensions">{t('dimensions')}</Label>
                  <Input id="dimensions" type="number" defaultValue="1536" />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="metric">{t('similarityMetric')}</Label>
                  <Select defaultValue="cosine">
                    <SelectTrigger id="metric">
                      <SelectValue placeholder={t('selectMetric')} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="cosine">{t('cosine')}</SelectItem>
                      <SelectItem value="euclidean">{t('euclidean')}</SelectItem>
                      <SelectItem value="dot">{t('dotProduct')}</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </div>
            <DialogFooter>
              <Button type="submit">{t('saveConnection')}</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>{common('error')}</AlertTitle>
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="connections">{t('connections')}</TabsTrigger>
          <TabsTrigger value="operations">{t('operations')}</TabsTrigger>
          <TabsTrigger value="settings">{t('settings')}</TabsTrigger>
        </TabsList>
        <TabsContent value="connections" className="space-y-4">
          {vectorDBs.map((db, index) => (
            <Card key={index}>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div>
                    <CardTitle>{db.name}</CardTitle>
                    <CardDescription>
                      {db.type.charAt(0).toUpperCase() + db.type.slice(1)} • {db.dimensions} {t('dimensions')} • {db.metric} {t('similarity')}
                    </CardDescription>
                  </div>
                  <div className="flex items-center gap-2">
                    <Switch 
                      checked={db.enabled} 
                      onCheckedChange={() => toggleDBStatus(index)}
                    />
                    <span>{db.enabled ? common('enabled') : common('disabled')}</span>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <h3 className="text-sm font-medium">{t('endpoint')}</h3>
                    <p className="text-sm text-muted-foreground mt-1">{db.endpoint || t('notConfigured')}</p>
                  </div>
                  <div>
                    <h3 className="text-sm font-medium">{t('namespace')}</h3>
                    <p className="text-sm text-muted-foreground mt-1">{db.namespace || t('notConfigured')}</p>
                  </div>
                  <div>
                    <h3 className="text-sm font-medium">{t('indexName')}</h3>
                    <p className="text-sm text-muted-foreground mt-1">{db.indexName || t('notConfigured')}</p>
                  </div>
                  <div>
                    <h3 className="text-sm font-medium">{t('apiKey')}</h3>
                    <p className="text-sm text-muted-foreground mt-1">{db.apiKey ? "••••••••••••••••••••" : t('notConfigured')}</p>
                  </div>
                </div>
              </CardContent>
              <CardFooter className="flex justify-between">
                <div>
                  <Badge variant={db.enabled ? "default" : "outline"}>
                    {db.enabled ? t('active') : t('inactive')}
                  </Badge>
                </div>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm">
                    <Edit className="h-4 w-4 mr-2" />
                    {common('edit')}
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => deleteVectorDB(index)}>
                    <Trash2 className="h-4 w-4 mr-2" />
                    {common('delete')}
                  </Button>
                </div>
              </CardFooter>
            </Card>
          ))}
        </TabsContent>
        <TabsContent value="operations" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>{t('vectorOperations')}</CardTitle>
              <CardDescription>
                {t('vectorOperationsDescription')}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-4">
                <div className="rounded-md border p-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="font-medium">{t('upsertVectors')}</h3>
                      <p className="text-sm text-muted-foreground mt-1">
                        {t('upsertVectorsDescription')}
                      </p>
                    </div>
                    <Button variant="outline">
                      <Play className="h-4 w-4 mr-2" />
                      {t('upsert')}
                    </Button>
                  </div>
                </div>
                
                <div className="rounded-md border p-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="font-medium">{t('queryVectors')}</h3>
                      <p className="text-sm text-muted-foreground mt-1">
                        {t('queryVectorsDescription')}
                      </p>
                    </div>
                    <Button variant="outline">
                      <Play className="h-4 w-4 mr-2" />
                      {t('query')}
                    </Button>
                  </div>
                </div>
                
                <div className="rounded-md border p-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="font-medium">{t('deleteVectors')}</h3>
                      <p className="text-sm text-muted-foreground mt-1">
                        {t('deleteVectorsDescription')}
                      </p>
                    </div>
                    <Button variant="outline">
                      <Play className="h-4 w-4 mr-2" />
                      {t('delete')}
                    </Button>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
          
          <Card>
            <CardHeader>
              <CardTitle>{t('embeddingModels')}</CardTitle>
              <CardDescription>
                {t('embeddingModelsDescription')}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <div className="rounded-md border p-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="font-medium">OpenAI - text-embedding-ada-002</h3>
                      <p className="text-sm text-muted-foreground mt-1">
                        1536 {t('dimensions')} | ${0.0001.toFixed(4)} / 1K {t('tokens')}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <Switch checked={true} />
                      <span>{common('enabled')}</span>
                    </div>
                  </div>
                </div>
                
                <div className="rounded-md border p-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="font-medium">OpenAI - text-embedding-3-small</h3>
                      <p className="text-sm text-muted-foreground mt-1">
                        1536 {t('dimensions')} | ${0.00002.toFixed(5)} / 1K {t('tokens')}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <Switch checked={true} />
                      <span>{common('enabled')}</span>
                    </div>
                  </div>
                </div>
                
                <div className="rounded-md border p-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="font-medium">OpenAI - text-embedding-3-large</h3>
                      <p className="text-sm text-muted-foreground mt-1">
                        3072 {t('dimensions')} | ${0.00013.toFixed(5)} / 1K {t('tokens')}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <Switch checked={false} />
                      <span>{common('disabled')}</span>
                    </div>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="settings" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>{t('globalSettings')}</CardTitle>
              <CardDescription>
                {t('globalSettingsDescription')}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center space-x-2">
                <Switch id="auto-embed" />
                <Label htmlFor="auto-embed">{t('autoEmbedding')}</Label>
              </div>
              <p className="text-sm text-muted-foreground">
                {t('autoEmbeddingDescription')}
              </p>
              
              <Separator />
              
              <div className="grid gap-2">
                <Label htmlFor="default-embedding-model">{t('defaultEmbeddingModel')}</Label>
                <Select defaultValue="text-embedding-ada-002">
                  <SelectTrigger id="default-embedding-model">
                    <SelectValue placeholder={t('selectEmbeddingModel')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="text-embedding-ada-002">OpenAI - text-embedding-ada-002</SelectItem>
                    <SelectItem value="text-embedding-3-small">OpenAI - text-embedding-3-small</SelectItem>
                    <SelectItem value="text-embedding-3-large">OpenAI - text-embedding-3-large</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              
              <div className="grid gap-2">
                <Label htmlFor="default-vector-db">{t('defaultVectorDB')}</Label>
                <Select defaultValue="product-kb">
                  <SelectTrigger id="default-vector-db">
                    <SelectValue placeholder={t('selectDefaultVectorDB')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="product-kb">Product Knowledge Base</SelectItem>
                    <SelectItem value="support-faq">Customer Support FAQ</SelectItem>
                    <SelectItem value="legal-docs">Legal Documents</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              
              <div className="grid gap-2">
                <Label htmlFor="chunk-size">{t('chunkSize')}</Label>
                <Input id="chunk-size" type="number" defaultValue="512" />
                <p className="text-xs text-muted-foreground">
                  {t('chunkSizeDescription')}
                </p>
              </div>
              
              <div className="grid gap-2">
                <Label htmlFor="chunk-overlap">{t('chunkOverlap')}</Label>
                <Input id="chunk-overlap" type="number" defaultValue="50" />
                <p className="text-xs text-muted-foreground">
                  {t('chunkOverlapDescription')}
                </p>
              </div>
            </CardContent>
            <CardFooter>
              <Button>{common('saveChanges')}</Button>
            </CardFooter>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
