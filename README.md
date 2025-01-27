# COMP API

**COMP** is a junior enterprise at [PUC-MG](https://www.pucminas.br/destaques/Paginas/default.aspx), specializing in on-demand software development, essentially operating as a *software house*. The organization comprises **Members**, who are students enrolled in programs under ICEI (Institute of Exact Sciences and Informatics) and actively contribute to COMP. 

Each Member holds a specific **Role**, defining their designated function within the company. Members collaborate on **Projects**, which are tailored solutions developed by the company to address specific problems faced by its clients. 

To ensure innovation and excellence in its solutions, COMP leverages a variety of **Technologies** throughout its project development process, always striving to push boundaries and deliver top-quality products.

## Authentication and Authorization

Members are registered by an administrator (`is_admin: true`), who sets up their credentials. Access to resources is controlled based on permission levels, which determine who can interact with them. Permissions can be categorized as:

- **Public**: Accessible by anyone without authentication.
- **Members and Admins**: Restricted to authenticated members, including administrators.
- **Admins Only**: Limited to administrators with elevated privileges.

### Authentication: Login Endpoint
**Endpoint**: `POST /api/members/login`  
**Description**:  
Authenticates a member using their email and password, returning a JWT token valid for 15 minutes.  

**Request Body**:
```json
{
  "email": "example@example.com",
  "password": "password123"
}
```

**Token Claims**:
- `id`: Member's unique identifier.
- `is_active`: Indicates if the member is active.
- `is_admin`: Indicates if the member has admin privileges.

---