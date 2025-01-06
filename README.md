# COMP API

## Client Script Usage

This script (`client.sh`) is designed to interact with a REST API running at `http://localhost:8080/api`.  
It uses `curl` to make HTTP requests and `jq` to pretty-print JSON responses.

Make sure you have:

- **curl** installed
- **jq** installed
- The script is executable: `chmod +x client.sh`

Below is a list of all available commands, their arguments, and example usages.

---

### Table of Contents

1. [Users](#users)

   - [login \<username> \<password>](#login)
   - [register \<username> \<password>](#register)
   - [get-users](#get-users)

2. [Members](#members)

   - [get-members](#get-members)
   - [get-member \<id>](#get-member-id)
   - [create-member \<json-file>](#create-member-json-file)
   - [update-member \<id> \<json-file>](#update-member-id-json-file)
   - [delete-member \<id>](#delete-member-id)

3. [CV Applications](#cv-applications)

   - [create-cv-app \<json-file>](#create-cv-app-json-file)
   - [get-cv-apps](#get-cv-apps)
   - [get-cv-resume \<filename>](#get-cv-resume-filename)

4. [Project Proposals](#project-proposals)

   - [create-proposal \<json-file>](#create-proposal-json-file)
   - [get-proposals](#get-proposals)


---

### Users

#### login

```
./client.sh login <username> <password>
```

- Logs in a user with the specified username and password.
- The script will store a JWT in `token.tmp` if successful.

**Example**:

```bash
./client.sh login aaa aaa
```

#### register

```
./client.sh register <username> <password>
```

- Registers a new user.
- Requires a valid JWT token in `token.tmp` (you must be logged in as an authorized user).

**Example**:

```bash
./client.sh register newuser newpass
```

#### get-users

```
./client.sh get-users
```

- Retrieves a list of all users.
- Requires a valid JWT token in `token.tmp`.

**Example**:

```bash
./client.sh get-users
```

---

## Members

### get-members

```
./client.sh get-members
```

- Fetches a list of all members.
- Does **not** require JWT (assuming your server allows public access to this route).

**Example**:

```bash
./client.sh get-members
```

### get-member <id>

```
./client.sh get-member <id>
```

- Fetches a single member by ID.

**Example**:

```bash
./client.sh get-member 1
```

### create-member <json-file>

```
./client.sh create-member <json-file>
```

- Creates a new member.
- Requires a valid JWT token in `token.tmp`.

**Example**:

`member.json`
```json
{
  "name": "John",
  "surname": "Doe",
  "role": "Developer",
  "imageProfile": "https://example.com/avatar.jpg",
  "course": "Computer Science",
  "description": "Enthusiastic developer",
  "instagramUrl": "https://instagram.com/johndoe",
  "githubUrl": "https://github.com/johndoe",
  "linkedinUrl": "https://linkedin.com/in/johndoe",
  "date": "2025-01-01",
  "isActive": true
}
```

```bash
./client.sh create-member member.json
```

### update-member <id> <json-file>

```
./client.sh update-member <id> <json-file>
```

- Updates an existing member by ID.
- Requires a valid JWT token in `token.tmp`.

**Example**:

`member.json`
```json
{
  "role": "Senior Developer",
  "isActive": false
}
```

```bash
./client.sh update-member 1 update_member.json
```

### delete-member <id>

```
./client.sh delete-member <id>
```

- Deletes a member by ID.
- Requires a valid JWT token in `token.tmp`.

**Example**:

```bash
./client.sh delete-member 2
```

---

## CV Applications

### create-cv-app <json-file>

```
./client.sh create-cv-app <json-file>
```

- Creates a new CV application (multipart/form-data) and uploads a PDF resume.
- The JSON file must contain `resume` as a **local file path** to the PDF.

**Example**:

`cv.json`
```json
{
  "fullName": "John Doe",
  "phone": "+1234567890",
  "linkedIn": "https://linkedin.com/in/johndoe",
  "gitHub": "https://github.com/johndoe",
  "course": "Computer Science",
  "period": "6",
  "resume": "my_resume.pdf"
}
```

```bash
./client.sh create-cv-app cv.json
```

### get-cv-apps

```
./client.sh get-cv-apps
```

- Fetches all CV applications.
- Requires a valid JWT token in `token.tmp`.

**Example**:

```bash
./client.sh get-cv-apps
```

### get-cv-resume <filename>

```
./client.sh get-cv-resume <filename>
```

- Downloads a resume from the server and saves it locally under the same filename.

**Example**:

```bash
./client.sh get-cv-resume resume.pdf
```

---

## Project Proposals

### create-proposal <json-file>

```
./client.sh create-proposal <json-file>
```

- Submits a new project proposal.

**Example**:

`proposal.json`
```json
{
  "fullName": "John Doe",
  "phone": "+1234567890",
  "projectDescription": "A mobile app to connect freelancers with clients",
  "appFeatures": "Messaging, payment, project tracking",
  "visualIdentity": "Yes",
  "budget": "$10,000"
}
```

```bash
./client.sh create-proposal proposal.json
```

### get-proposals

```
./client.sh get-proposals
```

- Fetches all project proposals.
- Requires a valid JWT token in `token.tmp`.

**Example**:

```bash
./client.sh get-proposals
```

---

