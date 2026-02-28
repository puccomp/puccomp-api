import swaggerJsdoc from 'swagger-jsdoc'

const options: swaggerJsdoc.Options = {
  definition: {
    openapi: '3.0.0',
    info: {
      title: 'PUC COMP API',
      version: '1.0.0',
      description:
        'REST API for PUC COMP, a junior software company at PUC-MG. Manages members, projects, technologies, roles, CV applications, project proposals, and image memories.',
    },
    servers: [{ url: '/api' }],
    components: {
      securitySchemes: {
        bearerAuth: {
          type: 'http',
          scheme: 'bearer',
          bearerFormat: 'JWT',
        },
      },
      schemas: {
        Member: {
          type: 'object',
          properties: {
            id: { type: 'integer', example: 1 },
            email: { type: 'string', format: 'email', example: 'joao@pucminas.br' },
            name: { type: 'string', example: 'Joao' },
            surname: { type: 'string', example: 'Silva' },
            bio: { type: 'string', nullable: true, example: 'Backend developer' },
            course: { type: 'string', example: 'Ciencia da Computacao' },
            avatar_url: { type: 'string', nullable: true, example: 'https://s3.amazonaws.com/...' },
            entry_date: { type: 'string', format: 'date', example: '2023-02-01' },
            exit_date: { type: 'string', format: 'date', nullable: true, example: '2024-12-01' },
            is_active: { type: 'boolean', example: true },
            github_url: { type: 'string', nullable: true, example: 'https://github.com/joao' },
            instagram_url: { type: 'string', nullable: true, example: 'https://instagram.com/joao' },
            linkedin_url: { type: 'string', nullable: true, example: 'https://linkedin.com/in/joao' },
            is_admin: { type: 'boolean', example: false },
            role_id: { type: 'integer', example: 2 },
            role: { type: 'string', nullable: true, example: 'Developer' },
          },
        },
        CreateMemberBody: {
          type: 'object',
          required: ['email', 'password', 'name', 'surname', 'course', 'role_id', 'entry_date'],
          properties: {
            email: { type: 'string', format: 'email', example: 'joao@pucminas.br' },
            password: { type: 'string', format: 'password', example: 'secret123' },
            name: { type: 'string', example: 'Joao' },
            surname: { type: 'string', example: 'Silva' },
            bio: { type: 'string', example: 'Backend developer' },
            course: { type: 'string', example: 'Ciencia da Computacao' },
            avatar_url: { type: 'string', example: 'https://s3.amazonaws.com/...' },
            entry_date: { type: 'string', format: 'date', example: '2023-02-01' },
            exit_date: { type: 'string', format: 'date', example: '2024-12-01' },
            is_active: { type: 'boolean', example: true },
            github_url: { type: 'string', example: 'https://github.com/joao' },
            instagram_url: { type: 'string', example: 'https://instagram.com/joao' },
            linkedin_url: { type: 'string', example: 'https://linkedin.com/in/joao' },
            is_admin: { type: 'boolean', example: false },
            role_id: { type: 'integer', example: 2 },
          },
        },
        Role: {
          type: 'object',
          properties: {
            id: { type: 'integer', example: 1 },
            name: { type: 'string', example: 'Developer' },
            description: { type: 'string', nullable: true, example: 'Software developer role' },
            level: { type: 'integer', example: 1 },
            createdAt: { type: 'string', format: 'date', example: '2023-01-01' },
            updatedAt: { type: 'string', format: 'date', example: '2023-06-15' },
          },
        },
        Technology: {
          type: 'object',
          properties: {
            id: { type: 'integer', example: 1 },
            name: { type: 'string', example: 'TypeScript' },
            iconUrl: { type: 'string', nullable: true, example: 'https://s3.amazonaws.com/...' },
            type: {
              type: 'string',
              enum: ['LANGUAGE', 'FRAMEWORK', 'LIBRARY', 'TOOL', 'OTHER'],
              example: 'LANGUAGE',
            },
          },
        },
        Project: {
          type: 'object',
          properties: {
            id: { type: 'integer', example: 1 },
            name: { type: 'string', example: 'puccomp-site' },
            description: { type: 'string', example: 'Official website of PUC COMP' },
            image_url: { type: 'string', nullable: true, example: 'https://s3.amazonaws.com/...' },
            contributors_url: { type: 'string', example: '/api/projects/puccomp-site/contributors' },
            technologies_url: { type: 'string', example: '/api/projects/puccomp-site/technologies' },
            createdAt: { type: 'string', format: 'date', example: '2023-01-01' },
            updatedAt: { type: 'string', format: 'date', example: '2024-01-01' },
          },
        },
        Contributor: {
          type: 'object',
          properties: {
            name: { type: 'string', example: 'Joao' },
            surname: { type: 'string', example: 'Silva' },
            avatar_url: { type: 'string', nullable: true, example: 'https://s3.amazonaws.com/...' },
            github_url: { type: 'string', nullable: true, example: 'https://github.com/joao' },
            member_url: { type: 'string', example: '/api/members/1' },
            is_active: { type: 'boolean', example: true },
          },
        },
        ProjectProposal: {
          type: 'object',
          properties: {
            id: { type: 'integer', example: 1 },
            name: { type: 'string', example: 'Maria Souza' },
            phone: { type: 'string', example: '+5531999999999' },
            description: { type: 'string', example: 'An e-commerce platform for local stores' },
            features: { type: 'string', example: 'Shopping cart, payments, user profiles' },
            visualIdentity: { type: 'string', nullable: true, example: 'Blue and white color scheme' },
            date: { type: 'string', format: 'date-time', example: '2024-03-15T12:00:00.000Z' },
          },
        },
        CvApplication: {
          type: 'object',
          properties: {
            fullname: { type: 'string', example: 'Carlos Ferreira' },
            phone: { type: 'string', example: '+5531988888888' },
            linkedin: { type: 'string', nullable: true, example: 'https://linkedin.com/in/carlos' },
            github: { type: 'string', nullable: true, example: 'https://github.com/carlos' },
            course: { type: 'string', example: 'Engenharia de Software' },
            period: { type: 'string', example: '5' },
            resume_url: { type: 'string', example: 'https://s3.amazonaws.com/...' },
          },
        },
        ImageMemory: {
          type: 'object',
          properties: {
            id: { type: 'integer', example: 1 },
            title: { type: 'string', example: 'Hackathon 2024' },
            description: { type: 'string', example: 'Team photo from our annual hackathon' },
            date: { type: 'string', example: '2024-05-10' },
            image_url: { type: 'string', example: 'https://s3.amazonaws.com/...' },
          },
        },
        Error: {
          type: 'object',
          properties: {
            message: { type: 'string', example: 'An error occurred.' },
          },
        },
      },
    },
    tags: [
      { name: 'Members', description: 'Member management' },
      { name: 'Projects', description: 'Project management' },
      { name: 'Roles', description: 'Role management' },
      { name: 'Technologies', description: 'Technology management' },
      { name: 'Memories', description: 'Image memory gallery' },
      { name: 'CV Applications', description: 'CV / resume submissions' },
      { name: 'Project Proposals', description: 'Client project proposals' },
    ],
    paths: {
      // ─────────────────────────────────────────────
      // MEMBERS
      // ─────────────────────────────────────────────
      '/members/login': {
        post: {
          tags: ['Members'],
          summary: 'Authenticate a member',
          requestBody: {
            required: true,
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  required: ['email', 'password'],
                  properties: {
                    email: { type: 'string', format: 'email', example: 'admin@pucminas.br' },
                    password: { type: 'string', format: 'password', example: 'secret123' },
                  },
                },
              },
            },
          },
          responses: {
            200: {
              description: 'Successful login — returns a JWT token',
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    properties: {
                      token: { type: 'string', example: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...' },
                    },
                  },
                },
              },
            },
            400: { description: 'Email and password are required', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Invalid credentials', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Member not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Internal server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      '/members': {
        post: {
          tags: ['Members'],
          summary: 'Create a new member (admin only)',
          security: [{ bearerAuth: [] }],
          description: 'Requires admin privileges.',
          requestBody: {
            required: true,
            content: {
              'application/json': {
                schema: { $ref: '#/components/schemas/CreateMemberBody' },
              },
            },
          },
          responses: {
            201: {
              description: 'Member created',
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    properties: {
                      message: { type: 'string', example: 'Member created successfully.' },
                      member_url: { type: 'string', example: 'http://localhost:8080/api/members/1' },
                    },
                  },
                },
              },
            },
            400: { description: 'Missing or invalid fields', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            403: { description: 'Forbidden — not an admin', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            409: { description: 'Email already exists', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        get: {
          tags: ['Members'],
          summary: 'List all members',
          responses: {
            200: {
              description: 'Array of members',
              content: {
                'application/json': {
                  schema: { type: 'array', items: { $ref: '#/components/schemas/Member' } },
                },
              },
            },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      '/members/{id}': {
        get: {
          tags: ['Members'],
          summary: 'Get a member by ID',
          parameters: [
            { name: 'id', in: 'path', required: true, schema: { type: 'integer' }, example: 1 },
          ],
          responses: {
            200: {
              description: 'Member data',
              content: { 'application/json': { schema: { $ref: '#/components/schemas/Member' } } },
            },
            400: { description: 'Invalid ID format', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Member not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        patch: {
          tags: ['Members'],
          summary: 'Update a member (admin only, partial update)',
          security: [{ bearerAuth: [] }],
          description: 'Requires admin privileges. All fields are optional — only provided fields will be updated.',
          parameters: [
            { name: 'id', in: 'path', required: true, schema: { type: 'integer' }, example: 1 },
          ],
          requestBody: {
            required: true,
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  properties: {
                    email: { type: 'string', format: 'email' },
                    password: { type: 'string', format: 'password' },
                    name: { type: 'string' },
                    surname: { type: 'string' },
                    bio: { type: 'string' },
                    course: { type: 'string' },
                    avatar_url: { type: 'string' },
                    entry_date: { type: 'string', format: 'date' },
                    exit_date: { type: 'string', format: 'date' },
                    is_active: { type: 'boolean' },
                    github_url: { type: 'string' },
                    instagram_url: { type: 'string' },
                    linkedin_url: { type: 'string' },
                    is_admin: { type: 'boolean' },
                    role_id: { type: 'integer' },
                  },
                },
              },
            },
          },
          responses: {
            200: {
              description: 'Member updated',
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    properties: {
                      message: { type: 'string', example: 'Member updated successfully.' },
                      member: { $ref: '#/components/schemas/Member' },
                    },
                  },
                },
              },
            },
            400: { description: 'No fields to update / validation error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            403: { description: 'Forbidden', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Member not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        delete: {
          tags: ['Members'],
          summary: 'Delete a member (admin only)',
          security: [{ bearerAuth: [] }],
          description: 'Requires admin privileges. Also removes all contributor records for this member.',
          parameters: [
            { name: 'id', in: 'path', required: true, schema: { type: 'integer' }, example: 1 },
          ],
          responses: {
            200: {
              description: 'Member deleted',
              content: { 'application/json': { schema: { type: 'object', properties: { message: { type: 'string', example: 'Member deleted successfully.' } } } } },
            },
            400: { description: 'Invalid ID format', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            403: { description: 'Forbidden', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Member not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      // ─────────────────────────────────────────────
      // PROJECTS
      // ─────────────────────────────────────────────
      '/projects': {
        post: {
          tags: ['Projects'],
          summary: 'Create a new project (auth required)',
          security: [{ bearerAuth: [] }],
          requestBody: {
            required: true,
            content: {
              'multipart/form-data': {
                schema: {
                  type: 'object',
                  required: ['name', 'description'],
                  properties: {
                    name: { type: 'string', example: 'puccomp-site', description: '3–50 chars, alphanumeric, hyphens or underscores allowed' },
                    description: { type: 'string', example: 'Official website of PUC COMP' },
                    created_at: { type: 'string', format: 'date', example: '2023-01-01' },
                    updated_at: { type: 'string', format: 'date', example: '2024-01-01' },
                    image: { type: 'string', format: 'binary', description: 'Optional project image' },
                  },
                },
              },
            },
          },
          responses: {
            201: {
              description: 'Project created',
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    properties: {
                      message: { type: 'string' },
                      project_id: { type: 'integer', example: 1 },
                      project_url: { type: 'string', example: 'http://localhost:8080/api/projects/puccomp-site' },
                    },
                  },
                },
              },
            },
            400: { description: 'Validation error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            409: { description: 'Project name already exists', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        get: {
          tags: ['Projects'],
          summary: 'List all projects',
          responses: {
            200: {
              description: 'Array of projects',
              content: { 'application/json': { schema: { type: 'array', items: { $ref: '#/components/schemas/Project' } } } },
            },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      '/projects/{project_name}': {
        get: {
          tags: ['Projects'],
          summary: 'Get a project by name',
          parameters: [
            { name: 'project_name', in: 'path', required: true, schema: { type: 'string' }, example: 'puccomp-site' },
          ],
          responses: {
            200: {
              description: 'Project data',
              content: { 'application/json': { schema: { $ref: '#/components/schemas/Project' } } },
            },
            404: { description: 'Project not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        patch: {
          tags: ['Projects'],
          summary: 'Update a project (auth required)',
          security: [{ bearerAuth: [] }],
          parameters: [
            { name: 'project_name', in: 'path', required: true, schema: { type: 'string' }, example: 'puccomp-site' },
          ],
          requestBody: {
            content: {
              'multipart/form-data': {
                schema: {
                  type: 'object',
                  properties: {
                    name: { type: 'string', example: 'puccomp-site-v2' },
                    description: { type: 'string', example: 'Updated description' },
                    created_at: { type: 'string', format: 'date' },
                    updated_at: { type: 'string', format: 'date' },
                    image: { type: 'string', format: 'binary', description: 'New project image — replaces the existing one' },
                  },
                },
              },
            },
          },
          responses: {
            200: {
              description: 'Project updated',
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    properties: {
                      message: { type: 'string', example: 'Project updated successfully.' },
                      project_url: { type: 'string' },
                    },
                  },
                },
              },
            },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Project not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            409: { description: 'New project name already exists', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        delete: {
          tags: ['Projects'],
          summary: 'Delete a project (auth required)',
          security: [{ bearerAuth: [] }],
          parameters: [
            { name: 'project_name', in: 'path', required: true, schema: { type: 'string' }, example: 'puccomp-site' },
          ],
          responses: {
            200: {
              description: 'Project deleted',
              content: { 'application/json': { schema: { type: 'object', properties: { message: { type: 'string', example: 'Project deleted successfully.' } } } } },
            },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Project not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      '/projects/{project_name}/contributors': {
        post: {
          tags: ['Projects'],
          summary: 'Add a contributor to a project (auth required)',
          security: [{ bearerAuth: [] }],
          parameters: [
            { name: 'project_name', in: 'path', required: true, schema: { type: 'string' }, example: 'puccomp-site' },
          ],
          requestBody: {
            required: true,
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  required: ['member_id'],
                  properties: {
                    member_id: { type: 'integer', example: 3 },
                  },
                },
              },
            },
          },
          responses: {
            201: {
              description: 'Contributor added',
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    properties: {
                      message: { type: 'string', example: 'Contributor added successfully.' },
                      data: {
                        type: 'object',
                        properties: {
                          memberId: { type: 'integer' },
                          projectId: { type: 'integer' },
                        },
                      },
                    },
                  },
                },
              },
            },
            400: { description: 'Member ID is required', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Project or member not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            409: { description: 'Member is already a contributor', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        get: {
          tags: ['Projects'],
          summary: 'List contributors of a project',
          parameters: [
            { name: 'project_name', in: 'path', required: true, schema: { type: 'string' }, example: 'puccomp-site' },
          ],
          responses: {
            200: {
              description: 'Array of contributors',
              content: { 'application/json': { schema: { type: 'array', items: { $ref: '#/components/schemas/Contributor' } } } },
            },
            404: { description: 'Project not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      '/projects/{project_name}/contributors/{member_id}': {
        delete: {
          tags: ['Projects'],
          summary: 'Remove a contributor from a project (auth required)',
          security: [{ bearerAuth: [] }],
          parameters: [
            { name: 'project_name', in: 'path', required: true, schema: { type: 'string' }, example: 'puccomp-site' },
            { name: 'member_id', in: 'path', required: true, schema: { type: 'integer' }, example: 3 },
          ],
          responses: {
            200: {
              description: 'Contributor removed',
              content: { 'application/json': { schema: { type: 'object', properties: { message: { type: 'string', example: 'Contributor removed successfully.' } } } } },
            },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Contributor or project not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      '/projects/{project_name}/technologies': {
        post: {
          tags: ['Projects'],
          summary: 'Add a technology to a project (auth required)',
          security: [{ bearerAuth: [] }],
          parameters: [
            { name: 'project_name', in: 'path', required: true, schema: { type: 'string' }, example: 'puccomp-site' },
          ],
          requestBody: {
            required: true,
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  required: ['technology_name', 'usage_level'],
                  properties: {
                    technology_name: { type: 'string', example: 'TypeScript' },
                    usage_level: {
                      type: 'string',
                      enum: ['PRIMARY', 'SECONDARY', 'SUPPORTIVE', 'OBSOLETE'],
                      example: 'PRIMARY',
                    },
                  },
                },
              },
            },
          },
          responses: {
            201: {
              description: 'Technology added to project',
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    properties: {
                      message: { type: 'string', example: 'Technology added successfully to the project.' },
                      project_id: { type: 'integer' },
                      technology_id: { type: 'integer' },
                    },
                  },
                },
              },
            },
            400: { description: 'Missing fields or invalid usage level', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Project or technology not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            409: { description: 'Technology already associated with project', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        get: {
          tags: ['Projects'],
          summary: 'List technologies of a project',
          parameters: [
            { name: 'project_name', in: 'path', required: true, schema: { type: 'string' }, example: 'puccomp-site' },
          ],
          responses: {
            200: {
              description: 'Array of project technologies',
              content: {
                'application/json': {
                  schema: {
                    type: 'array',
                    items: {
                      type: 'object',
                      properties: {
                        projectId: { type: 'integer' },
                        technologyId: { type: 'integer' },
                        usageLevel: { type: 'string', enum: ['PRIMARY', 'SECONDARY', 'SUPPORTIVE', 'OBSOLETE'] },
                        technology: { $ref: '#/components/schemas/Technology' },
                      },
                    },
                  },
                },
              },
            },
            404: { description: 'Project not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      '/projects/{project_name}/technologies/{technology_id}': {
        delete: {
          tags: ['Projects'],
          summary: 'Remove a technology from a project (auth required)',
          security: [{ bearerAuth: [] }],
          parameters: [
            { name: 'project_name', in: 'path', required: true, schema: { type: 'string' }, example: 'puccomp-site' },
            { name: 'technology_id', in: 'path', required: true, schema: { type: 'integer' }, example: 1 },
          ],
          responses: {
            200: {
              description: 'Technology removed from project',
              content: { 'application/json': { schema: { type: 'object', properties: { message: { type: 'string', example: 'Technology removed successfully.' } } } } },
            },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Technology not associated with this project', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      // ─────────────────────────────────────────────
      // ROLES
      // ─────────────────────────────────────────────
      '/roles': {
        post: {
          tags: ['Roles'],
          summary: 'Create a new role (admin only)',
          security: [{ bearerAuth: [] }],
          description: 'Requires admin privileges.',
          requestBody: {
            required: true,
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  required: ['name', 'level'],
                  properties: {
                    name: { type: 'string', example: 'Developer' },
                    description: { type: 'string', example: 'Software developer role' },
                    level: { type: 'integer', minimum: 0, example: 1 },
                  },
                },
              },
            },
          },
          responses: {
            201: {
              description: 'Role created',
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    properties: {
                      message: { type: 'string', example: 'Role created successfully.' },
                      role_url: { type: 'string', example: 'http://localhost:8080/api/roles/1' },
                    },
                  },
                },
              },
            },
            400: { description: 'Validation error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            403: { description: 'Forbidden', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            409: { description: 'Role name already exists', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        get: {
          tags: ['Roles'],
          summary: 'List all roles',
          responses: {
            200: {
              description: 'Array of roles',
              content: { 'application/json': { schema: { type: 'array', items: { $ref: '#/components/schemas/Role' } } } },
            },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      '/roles/{id}': {
        get: {
          tags: ['Roles'],
          summary: 'Get a role by ID',
          parameters: [
            { name: 'id', in: 'path', required: true, schema: { type: 'integer' }, example: 1 },
          ],
          responses: {
            200: {
              description: 'Role data',
              content: { 'application/json': { schema: { $ref: '#/components/schemas/Role' } } },
            },
            400: { description: 'Invalid ID', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Role not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        patch: {
          tags: ['Roles'],
          summary: 'Update a role (admin only)',
          security: [{ bearerAuth: [] }],
          description: 'Requires admin privileges.',
          parameters: [
            { name: 'id', in: 'path', required: true, schema: { type: 'integer' }, example: 1 },
          ],
          requestBody: {
            required: true,
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  properties: {
                    name: { type: 'string', example: 'Senior Developer' },
                    description: { type: 'string', example: 'Updated description' },
                    level: { type: 'integer', minimum: 0, example: 2 },
                  },
                },
              },
            },
          },
          responses: {
            200: {
              description: 'Role updated',
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    properties: {
                      message: { type: 'string', example: 'Role updated successfully.' },
                      role_url: { type: 'string' },
                    },
                  },
                },
              },
            },
            400: { description: 'Validation error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            403: { description: 'Forbidden', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Role not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            409: { description: 'Role name already exists', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        delete: {
          tags: ['Roles'],
          summary: 'Delete a role (admin only)',
          security: [{ bearerAuth: [] }],
          description: 'Requires admin privileges. Fails if members are still assigned to this role.',
          parameters: [
            { name: 'id', in: 'path', required: true, schema: { type: 'integer' }, example: 1 },
          ],
          responses: {
            200: {
              description: 'Role deleted',
              content: { 'application/json': { schema: { type: 'object', properties: { message: { type: 'string', example: 'Role deleted successfully.' } } } } },
            },
            400: { description: 'Invalid ID or role has members', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            403: { description: 'Forbidden', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Role not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      // ─────────────────────────────────────────────
      // TECHNOLOGIES
      // ─────────────────────────────────────────────
      '/technologies': {
        post: {
          tags: ['Technologies'],
          summary: 'Create a new technology (auth required)',
          security: [{ bearerAuth: [] }],
          requestBody: {
            required: true,
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  required: ['name', 'type'],
                  properties: {
                    name: { type: 'string', example: 'TypeScript' },
                    icon_url: { type: 'string', example: 'https://s3.amazonaws.com/...' },
                    type: {
                      type: 'string',
                      enum: ['LANGUAGE', 'FRAMEWORK', 'LIBRARY', 'TOOL', 'OTHER'],
                      example: 'LANGUAGE',
                    },
                  },
                },
              },
            },
          },
          responses: {
            201: {
              description: 'Technology created',
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    properties: {
                      message: { type: 'string', example: 'Technology created successfully.' },
                      technology_url: { type: 'string', example: 'http://localhost:8080/api/technologies/1' },
                    },
                  },
                },
              },
            },
            400: { description: 'Validation error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            409: { description: 'Technology name already exists', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        get: {
          tags: ['Technologies'],
          summary: 'List all technologies',
          responses: {
            200: {
              description: 'Array of technologies (sorted by name)',
              content: { 'application/json': { schema: { type: 'array', items: { $ref: '#/components/schemas/Technology' } } } },
            },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      '/technologies/{id}': {
        patch: {
          tags: ['Technologies'],
          summary: 'Update a technology (auth required)',
          security: [{ bearerAuth: [] }],
          parameters: [
            { name: 'id', in: 'path', required: true, schema: { type: 'integer' }, example: 1 },
          ],
          requestBody: {
            required: true,
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  properties: {
                    name: { type: 'string', example: 'TypeScript' },
                    icon_url: { type: 'string', example: 'https://s3.amazonaws.com/...' },
                    type: {
                      type: 'string',
                      enum: ['LANGUAGE', 'FRAMEWORK', 'LIBRARY', 'TOOL', 'OTHER'],
                    },
                  },
                },
              },
            },
          },
          responses: {
            200: {
              description: 'Technology updated',
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    properties: {
                      message: { type: 'string', example: 'Technology updated successfully.' },
                      technology_url: { type: 'string' },
                    },
                  },
                },
              },
            },
            400: { description: 'Invalid ID or type', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Technology not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            409: { description: 'Technology name already exists', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        delete: {
          tags: ['Technologies'],
          summary: 'Delete a technology (auth required)',
          security: [{ bearerAuth: [] }],
          description: 'Fails if the technology is used by any project.',
          parameters: [
            { name: 'id', in: 'path', required: true, schema: { type: 'integer' }, example: 1 },
          ],
          responses: {
            200: {
              description: 'Technology deleted',
              content: { 'application/json': { schema: { type: 'object', properties: { message: { type: 'string', example: 'Technology deleted successfully.' } } } } },
            },
            400: { description: 'Technology is used by projects', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Technology not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      // ─────────────────────────────────────────────
      // MEMORIES
      // ─────────────────────────────────────────────
      '/memories': {
        post: {
          tags: ['Memories'],
          summary: 'Upload a memory image (admin only)',
          security: [{ bearerAuth: [] }],
          description: 'Requires admin privileges.',
          requestBody: {
            required: true,
            content: {
              'multipart/form-data': {
                schema: {
                  type: 'object',
                  required: ['title', 'description', 'date', 'image'],
                  properties: {
                    title: { type: 'string', example: 'Hackathon 2024' },
                    description: { type: 'string', example: 'Team photo from our annual hackathon' },
                    date: { type: 'string', example: '2024-05-10' },
                    image: { type: 'string', format: 'binary', description: 'Image file (required)' },
                  },
                },
              },
            },
          },
          responses: {
            201: {
              description: 'Memory image uploaded',
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    properties: {
                      message: { type: 'string', example: 'Memory image uploaded successfully.' },
                      image_url: { type: 'string', example: 'https://s3.amazonaws.com/...' },
                    },
                  },
                },
              },
            },
            400: { description: 'File required', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            403: { description: 'Forbidden', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            409: { description: 'Image with this name already exists', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        get: {
          tags: ['Memories'],
          summary: 'List all memory images',
          parameters: [
            {
              name: 'sort_by',
              in: 'query',
              required: false,
              schema: { type: 'string', enum: ['date', 'title', 'id'], default: 'date' },
              description: 'Field to sort by (default: date)',
            },
            {
              name: 'order',
              in: 'query',
              required: false,
              schema: { type: 'string', enum: ['asc', 'desc'], default: 'desc' },
              description: 'Sort direction (default: desc)',
            },
          ],
          responses: {
            200: {
              description: 'Array of memory images',
              content: { 'application/json': { schema: { type: 'array', items: { $ref: '#/components/schemas/ImageMemory' } } } },
            },
            400: { description: 'Invalid sort_by or order value', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      '/memories/{id}': {
        delete: {
          tags: ['Memories'],
          summary: 'Delete a memory image (admin only)',
          security: [{ bearerAuth: [] }],
          description: 'Requires admin privileges. Also deletes the image from S3.',
          parameters: [
            { name: 'id', in: 'path', required: true, schema: { type: 'integer' }, example: 1 },
          ],
          responses: {
            200: {
              description: 'Memory image deleted',
              content: {
                'application/json': {
                  schema: {
                    type: 'object',
                    properties: {
                      id: { type: 'integer', example: 1 },
                      message: { type: 'string', example: 'Image deleted successfully.' },
                    },
                  },
                },
              },
            },
            400: { description: 'Invalid ID', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            403: { description: 'Forbidden', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Image not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      // ─────────────────────────────────────────────
      // CV APPLICATIONS
      // ─────────────────────────────────────────────
      '/cv-applications': {
        post: {
          tags: ['CV Applications'],
          summary: 'Submit a CV application',
          description: 'Accepts a PDF file (max 5 MB). Sends a notification email to the team.',
          requestBody: {
            required: true,
            content: {
              'multipart/form-data': {
                schema: {
                  type: 'object',
                  required: ['fullName', 'phone', 'course', 'period', 'resume'],
                  properties: {
                    fullName: { type: 'string', example: 'Carlos Ferreira' },
                    phone: { type: 'string', example: '+5531988888888' },
                    linkedIn: { type: 'string', example: 'https://linkedin.com/in/carlos' },
                    gitHub: { type: 'string', example: 'https://github.com/carlos' },
                    course: { type: 'string', example: 'Engenharia de Software' },
                    period: { type: 'string', example: '5' },
                    resume: { type: 'string', format: 'binary', description: 'PDF file, max 5 MB' },
                  },
                },
              },
            },
          },
          responses: {
            200: {
              description: 'CV submitted successfully',
              content: { 'application/json': { schema: { type: 'object', properties: { message: { type: 'string', example: 'CV uploaded successfully' } } } } },
            },
            400: { description: 'Missing required fields or invalid file type', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            413: { description: 'File exceeds 5 MB limit', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        get: {
          tags: ['CV Applications'],
          summary: 'List all CV applications (admin only)',
          security: [{ bearerAuth: [] }],
          description: 'Requires admin privileges. Resume URLs are pre-signed S3 links.',
          responses: {
            200: {
              description: 'Array of CV applications',
              content: { 'application/json': { schema: { type: 'array', items: { $ref: '#/components/schemas/CvApplication' } } } },
            },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            403: { description: 'Forbidden', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      // ─────────────────────────────────────────────
      // PROJECT PROPOSALS
      // ─────────────────────────────────────────────
      '/project-proposals': {
        post: {
          tags: ['Project Proposals'],
          summary: 'Submit a project proposal',
          description: 'Public endpoint. Sends a notification email to the team.',
          requestBody: {
            required: true,
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  required: ['fullName', 'phone', 'projectDescription', 'appFeatures'],
                  properties: {
                    fullName: { type: 'string', example: 'Maria Souza' },
                    phone: { type: 'string', example: '+5531999999999' },
                    projectDescription: { type: 'string', example: 'An e-commerce platform for local stores' },
                    appFeatures: { type: 'string', example: 'Shopping cart, payments, user profiles' },
                    visualIdentity: { type: 'string', example: 'Blue and white color scheme' },
                  },
                },
              },
            },
          },
          responses: {
            200: {
              description: 'Proposal submitted',
              content: { 'application/json': { schema: { type: 'object', properties: { message: { type: 'string', example: 'Data saved successfully' } } } } },
            },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
        get: {
          tags: ['Project Proposals'],
          summary: 'List all project proposals (auth required)',
          security: [{ bearerAuth: [] }],
          responses: {
            200: {
              description: 'Array of project proposals',
              content: { 'application/json': { schema: { type: 'array', items: { $ref: '#/components/schemas/ProjectProposal' } } } },
            },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
      '/project-proposals/{id}': {
        get: {
          tags: ['Project Proposals'],
          summary: 'Get a project proposal by ID (auth required)',
          security: [{ bearerAuth: [] }],
          parameters: [
            { name: 'id', in: 'path', required: true, schema: { type: 'integer' }, example: 1 },
          ],
          responses: {
            200: {
              description: 'Project proposal data',
              content: { 'application/json': { schema: { $ref: '#/components/schemas/ProjectProposal' } } },
            },
            401: { description: 'Unauthorized', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            404: { description: 'Proposal not found', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
            500: { description: 'Server error', content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } } },
          },
        },
      },
    },
  },
  apis: [],
}

export const swaggerSpec = swaggerJsdoc(options)
