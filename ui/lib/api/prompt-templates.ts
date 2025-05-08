/**
 * Prompt Templates API client
 */

import { ApiResponse } from "@/lib/types/api";

// Prompt Template types
export type TemplateStatus = 'active' | 'draft' | 'archived';
export type TemplateVisibility = 'public' | 'private' | 'team';

export interface TemplateVariable {
  name: string;
  description: string;
  required: boolean;
  defaultValue?: string;
  type: 'string' | 'number' | 'boolean' | 'array' | 'object';
  options?: string[]; // For enum-like variables
}

export interface PromptTemplate {
  id: string;
  name: string;
  description: string;
  template: string;
  systemMessage?: string;
  variables: TemplateVariable[];
  tags: string[];
  status: TemplateStatus;
  visibility: TemplateVisibility;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  version: number;
  modelId?: string; // Optional specific model to use
  usageCount: number;
}

// API endpoints
const API_BASE = '/api';
const TEMPLATES_ENDPOINT = `${API_BASE}/ai/templates`;

/**
 * Get all prompt templates
 */
export async function getTemplates(): Promise<ApiResponse<PromptTemplate[]>> {
  try {
    const response = await fetch(TEMPLATES_ENDPOINT);
    
    if (!response.ok) {
      throw new Error(`Failed to fetch templates: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.templates || []
    };
  } catch (error) {
    console.error('Error fetching templates:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Get template by ID
 */
export async function getTemplate(id: string): Promise<ApiResponse<PromptTemplate>> {
  try {
    const response = await fetch(`${TEMPLATES_ENDPOINT}/${id}`);
    
    if (!response.ok) {
      throw new Error(`Failed to fetch template: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.template
    };
  } catch (error) {
    console.error(`Error fetching template ${id}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Create a new prompt template
 */
export async function createTemplate(template: Omit<PromptTemplate, 'id' | 'createdAt' | 'updatedAt' | 'version' | 'usageCount'>): Promise<ApiResponse<PromptTemplate>> {
  try {
    const response = await fetch(TEMPLATES_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(template)
    });
    
    if (!response.ok) {
      throw new Error(`Failed to create template: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.template
    };
  } catch (error) {
    console.error('Error creating template:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Update an existing prompt template
 */
export async function updateTemplate(id: string, template: Partial<PromptTemplate>): Promise<ApiResponse<PromptTemplate>> {
  try {
    const response = await fetch(`${TEMPLATES_ENDPOINT}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(template)
    });
    
    if (!response.ok) {
      throw new Error(`Failed to update template: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.template
    };
  } catch (error) {
    console.error(`Error updating template ${id}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Delete a prompt template
 */
export async function deleteTemplate(id: string): Promise<ApiResponse<void>> {
  try {
    const response = await fetch(`${TEMPLATES_ENDPOINT}/${id}`, {
      method: 'DELETE'
    });
    
    if (!response.ok) {
      throw new Error(`Failed to delete template: ${response.statusText}`);
    }
    
    return {
      success: true
    };
  } catch (error) {
    console.error(`Error deleting template ${id}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

// Mock data for development
export const mockTemplates: PromptTemplate[] = [
  {
    id: "template-1",
    name: "Customer Support Response",
    description: "Template for generating customer support responses",
    template: "Please help the customer with their {{issue}} regarding {{product}}. The customer's tone is {{tone}}. Respond in a {{style}} manner.",
    systemMessage: "You are a helpful customer support agent for our company. Always be polite and professional.",
    variables: [
      {
        name: "issue",
        description: "The customer's issue",
        required: true,
        type: "string"
      },
      {
        name: "product",
        description: "The product the customer is having issues with",
        required: true,
        type: "string"
      },
      {
        name: "tone",
        description: "The customer's tone",
        required: false,
        defaultValue: "neutral",
        type: "string",
        options: ["angry", "confused", "neutral", "happy"]
      },
      {
        name: "style",
        description: "The style of the response",
        required: false,
        defaultValue: "professional",
        type: "string",
        options: ["professional", "casual", "technical"]
      }
    ],
    tags: ["customer-support", "response"],
    status: "active",
    visibility: "team",
    createdBy: "user-1",
    createdAt: "2024-04-15T10:30:00Z",
    updatedAt: "2024-04-15T10:30:00Z",
    version: 1,
    modelId: "gpt-4",
    usageCount: 1250
  },
  {
    id: "template-2",
    name: "Product Description Generator",
    description: "Template for generating product descriptions",
    template: "Generate a {{length}} product description for {{product_name}}, which is a {{product_type}}. The target audience is {{audience}}. The key features are: {{features}}.",
    systemMessage: "You are a marketing copywriter specializing in compelling product descriptions.",
    variables: [
      {
        name: "length",
        description: "The length of the description",
        required: false,
        defaultValue: "medium",
        type: "string",
        options: ["short", "medium", "long"]
      },
      {
        name: "product_name",
        description: "The name of the product",
        required: true,
        type: "string"
      },
      {
        name: "product_type",
        description: "The type of product",
        required: true,
        type: "string"
      },
      {
        name: "audience",
        description: "The target audience",
        required: true,
        type: "string"
      },
      {
        name: "features",
        description: "Key features of the product",
        required: true,
        type: "array"
      }
    ],
    tags: ["marketing", "product", "description"],
    status: "active",
    visibility: "public",
    createdBy: "user-2",
    createdAt: "2024-04-10T14:45:00Z",
    updatedAt: "2024-04-12T09:15:00Z",
    version: 2,
    usageCount: 875
  },
  {
    id: "template-3",
    name: "Code Review Assistant",
    description: "Template for generating code reviews",
    template: "Review the following {{language}} code:\n\n```{{language}}\n{{code}}\n```\n\nFocus on {{focus_areas}}. The code is for a {{project_type}} project.",
    systemMessage: "You are an experienced software engineer providing code reviews. Be thorough but constructive in your feedback.",
    variables: [
      {
        name: "language",
        description: "The programming language",
        required: true,
        type: "string",
        options: ["python", "javascript", "typescript", "java", "go", "rust", "c++", "other"]
      },
      {
        name: "code",
        description: "The code to review",
        required: true,
        type: "string"
      },
      {
        name: "focus_areas",
        description: "Areas to focus on in the review",
        required: false,
        defaultValue: "performance, security, readability",
        type: "string"
      },
      {
        name: "project_type",
        description: "The type of project",
        required: true,
        type: "string"
      }
    ],
    tags: ["development", "code-review"],
    status: "active",
    visibility: "team",
    createdBy: "user-3",
    createdAt: "2024-04-05T11:20:00Z",
    updatedAt: "2024-04-05T11:20:00Z",
    version: 1,
    modelId: "claude-3-opus",
    usageCount: 450
  }
];
