"use client"

import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { ApiDocumentation } from "@/components/api-docs/api-documentation"

// Sample API endpoints for demonstration
const sampleEndpoints = [
  {
    id: "get-users",
    path: "/api/v1/users",
    method: "GET",
    summary: "Get all users",
    description: "Returns a list of all users in the system.",
    tags: ["Users", "Core"],
    parameters: [
      {
        name: "limit",
        in: "query",
        description: "Maximum number of users to return",
        required: false,
        schema: {
          type: "integer",
          format: "int32"
        },
        example: 10
      },
      {
        name: "offset",
        in: "query",
        description: "Number of users to skip",
        required: false,
        schema: {
          type: "integer",
          format: "int32"
        },
        example: 0
      }
    ],
    responses: {
      "200": {
        description: "Successful operation",
        content: {
          "application/json": {
            schema: {
              type: "object",
              properties: {
                users: {
                  type: "array",
                  items: {
                    type: "object",
                    properties: {
                      id: { type: "string" },
                      name: { type: "string" },
                      email: { type: "string" }
                    }
                  }
                },
                total: { type: "integer" }
              },
              example: {
                users: [
                  { id: "1", name: "John Doe", email: "john@example.com" },
                  { id: "2", name: "Jane Smith", email: "jane@example.com" }
                ],
                total: 2
              }
            }
          }
        }
      },
      "401": {
        description: "Unauthorized",
        content: {
          "application/json": {
            schema: {
              type: "object",
              properties: {
                error: { type: "string" }
              },
              example: {
                error: "Unauthorized"
              }
            }
          }
        }
      }
    },
    security: [
      { "bearerAuth": [] }
    ]
  },
  {
    id: "get-user",
    path: "/api/v1/users/{id}",
    method: "GET",
    summary: "Get user by ID",
    description: "Returns a single user by ID.",
    tags: ["Users", "Core"],
    parameters: [
      {
        name: "id",
        in: "path",
        description: "ID of the user to return",
        required: true,
        schema: {
          type: "string"
        }
      }
    ],
    responses: {
      "200": {
        description: "Successful operation",
        content: {
          "application/json": {
            schema: {
              type: "object",
              properties: {
                id: { type: "string" },
                name: { type: "string" },
                email: { type: "string" }
              },
              example: {
                id: "1",
                name: "John Doe",
                email: "john@example.com"
              }
            }
          }
        }
      },
      "404": {
        description: "User not found",
        content: {
          "application/json": {
            schema: {
              type: "object",
              properties: {
                error: { type: "string" }
              },
              example: {
                error: "User not found"
              }
            }
          }
        }
      }
    },
    security: [
      { "bearerAuth": [] }
    ]
  },
  {
    id: "create-user",
    path: "/api/v1/users",
    method: "POST",
    summary: "Create a new user",
    description: "Creates a new user in the system.",
    tags: ["Users", "Core"],
    requestBody: {
      description: "User object to be created",
      required: true,
      content: {
        "application/json": {
          schema: {
            type: "object",
            required: ["name", "email"],
            properties: {
              name: { type: "string" },
              email: { type: "string" }
            },
            example: {
              name: "John Doe",
              email: "john@example.com"
            }
          }
        }
      }
    },
    responses: {
      "201": {
        description: "User created",
        content: {
          "application/json": {
            schema: {
              type: "object",
              properties: {
                id: { type: "string" },
                name: { type: "string" },
                email: { type: "string" }
              },
              example: {
                id: "1",
                name: "John Doe",
                email: "john@example.com"
              }
            }
          }
        }
      },
      "400": {
        description: "Invalid input",
        content: {
          "application/json": {
            schema: {
              type: "object",
              properties: {
                error: { type: "string" }
              },
              example: {
                error: "Invalid input"
              }
            }
          }
        }
      }
    },
    security: [
      { "bearerAuth": [] }
    ]
  },
  {
    id: "update-user",
    path: "/api/v1/users/{id}",
    method: "PUT",
    summary: "Update an existing user",
    description: "Updates an existing user in the system.",
    tags: ["Users", "Core"],
    parameters: [
      {
        name: "id",
        in: "path",
        description: "ID of the user to update",
        required: true,
        schema: {
          type: "string"
        }
      }
    ],
    requestBody: {
      description: "User object to be updated",
      required: true,
      content: {
        "application/json": {
          schema: {
            type: "object",
            properties: {
              name: { type: "string" },
              email: { type: "string" }
            },
            example: {
              name: "John Doe",
              email: "john@example.com"
            }
          }
        }
      }
    },
    responses: {
      "200": {
        description: "User updated",
        content: {
          "application/json": {
            schema: {
              type: "object",
              properties: {
                id: { type: "string" },
                name: { type: "string" },
                email: { type: "string" }
              },
              example: {
                id: "1",
                name: "John Doe",
                email: "john@example.com"
              }
            }
          }
        }
      },
      "404": {
        description: "User not found",
        content: {
          "application/json": {
            schema: {
              type: "object",
              properties: {
                error: { type: "string" }
              },
              example: {
                error: "User not found"
              }
            }
          }
        }
      }
    },
    security: [
      { "bearerAuth": [] }
    ]
  },
  {
    id: "delete-user",
    path: "/api/v1/users/{id}",
    method: "DELETE",
    summary: "Delete a user",
    description: "Deletes a user from the system.",
    tags: ["Users", "Core"],
    parameters: [
      {
        name: "id",
        in: "path",
        description: "ID of the user to delete",
        required: true,
        schema: {
          type: "string"
        }
      }
    ],
    responses: {
      "204": {
        description: "User deleted"
      },
      "404": {
        description: "User not found",
        content: {
          "application/json": {
            schema: {
              type: "object",
              properties: {
                error: { type: "string" }
              },
              example: {
                error: "User not found"
              }
            }
          }
        }
      }
    },
    security: [
      { "bearerAuth": [] }
    ]
  },
  {
    id: "get-models",
    path: "/api/v1/ai/models",
    method: "GET",
    summary: "Get all AI models",
    description: "Returns a list of all available AI models.",
    tags: ["AI", "Models"],
    responses: {
      "200": {
        description: "Successful operation",
        content: {
          "application/json": {
            schema: {
              type: "object",
              properties: {
                models: {
                  type: "array",
                  items: {
                    type: "object",
                    properties: {
                      id: { type: "string" },
                      name: { type: "string" },
                      provider: { type: "string" },
                      maxTokens: { type: "integer" }
                    }
                  }
                }
              },
              example: {
                models: [
                  { id: "gpt-4", name: "GPT-4", provider: "OpenAI", maxTokens: 8192 },
                  { id: "gpt-3.5-turbo", name: "GPT-3.5 Turbo", provider: "OpenAI", maxTokens: 4096 }
                ]
              }
            }
          }
        }
      }
    }
  },
  {
    id: "get-model",
    path: "/api/v1/ai/models/{id}",
    method: "GET",
    summary: "Get AI model by ID",
    description: "Returns a single AI model by ID.",
    tags: ["AI", "Models"],
    parameters: [
      {
        name: "id",
        in: "path",
        description: "ID of the model to return",
        required: true,
        schema: {
          type: "string"
        }
      }
    ],
    responses: {
      "200": {
        description: "Successful operation",
        content: {
          "application/json": {
            schema: {
              type: "object",
              properties: {
                id: { type: "string" },
                name: { type: "string" },
                provider: { type: "string" },
                maxTokens: { type: "integer" }
              },
              example: {
                id: "gpt-4",
                name: "GPT-4",
                provider: "OpenAI",
                maxTokens: 8192
              }
            }
          }
        }
      },
      "404": {
        description: "Model not found",
        content: {
          "application/json": {
            schema: {
              type: "object",
              properties: {
                error: { type: "string" }
              },
              example: {
                error: "Model not found"
              }
            }
          }
        }
      }
    }
  },
  {
    id: "get-routes",
    path: "/api/v1/routes",
    method: "GET",
    summary: "Get all routes",
    description: "Returns a list of all routes in the system.",
    tags: ["Routes", "Core"],
    deprecated: true,
    responses: {
      "200": {
        description: "Successful operation",
        content: {
          "application/json": {
            schema: {
              type: "object",
              properties: {
                routes: {
                  type: "array",
                  items: {
                    type: "object",
                    properties: {
                      id: { type: "string" },
                      path: { type: "string" },
                      target: { type: "string" }
                    }
                  }
                }
              },
              example: {
                routes: [
                  { id: "1", path: "/api/users", target: "user-service" },
                  { id: "2", path: "/api/products", target: "product-service" }
                ]
              }
            }
          }
        }
      }
    },
    security: [
      { "bearerAuth": [] }
    ]
  }
];

export default function ApiDocsPage() {
  return (
    <DashboardLayout>
      <ApiDocumentation 
        endpoints={sampleEndpoints}
        title="APIX API Documentation"
        description="Explore and test the APIX Gateway API endpoints"
        version="1.0.0"
      />
    </DashboardLayout>
  )
}
