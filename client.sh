#!/usr/bin/env bash

BASE_URL="http://localhost:8080/api"
TOKEN_FILE="./token.tmp"

usage() {
  echo "Usage: $0 <command> [args]"
  echo
  echo "Commands:"
  echo "  login <username> <password>"
  echo "  register <username> <password>"
  echo "  get-users"
  echo
  echo "  get-members"
  echo "  get-member <id>"
  echo "  create-member <json-file>"
  echo "  update-member <id> <json-file>"
  echo "  delete-member <id>"
  echo
  echo "  create-cv-app <json-file>"
  echo "  get-cv-apps"
  echo "  get-cv-resume <filename>"
  echo
  echo "  create-proposal <json-file>"
  echo "  get-proposals"
  echo
  echo '  *_cv.json "resume" field must be your local filepath cvfile.pdf'
  exit 1
}

login() {
  local user=$1
  local pass=$2
  local response
  response=$(curl -s -X POST "$BASE_URL/users/login" \
    -H "Content-Type: application/json" \
    -d '{
          "username": "'"$user"'",
          "password": "'"$pass"'"
        }')
  echo "$response" | jq .
  local token
  token=$(echo "$response" | jq -r '.token // empty')
  if [ -n "$token" ]; then
    echo "$token" > "$TOKEN_FILE"
    echo "Token saved to $TOKEN_FILE"
  else
    echo "Failed to login or retrieve token"
  fi
}

register_user() {
  local user=$1
  local pass=$2
  local token
  token=$(cat "$TOKEN_FILE" 2>/dev/null)
  local response
  response=$(curl -s -X POST "$BASE_URL/users/register" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d '{
          "username": "'"$user"'",
          "password": "'"$pass"'"
        }')
  echo "$response" | jq .
}

get_users() {
  local token
  token=$(cat "$TOKEN_FILE" 2>/dev/null)
  local response
  response=$(curl -s "$BASE_URL/users" \
    -H "Authorization: Bearer $token")
  echo "$response" | jq .
}

get_members() {
  local response
  response=$(curl -s "$BASE_URL/members")
  echo "$response" | jq .
}

get_member() {
  local id=$1
  local response
  response=$(curl -s "$BASE_URL/members/$id")
  echo "$response" | jq .
}

create_member() {
  local json_file=$1
  local token
  token=$(cat "$TOKEN_FILE" 2>/dev/null)
  local response
  response=$(curl -s -X POST "$BASE_URL/members" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d @"$json_file")
  echo "$response" | jq .
}

update_member() {
  local id=$1
  local json_file=$2
  local token
  token=$(cat "$TOKEN_FILE" 2>/dev/null)
  local response
  response=$(curl -s -X PUT "$BASE_URL/members/$id" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d @"$json_file")
  echo "$response" | jq .
}

delete_member() {
  local id=$1
  local token
  token=$(cat "$TOKEN_FILE" 2>/dev/null)
  local response
  response=$(curl -s -X DELETE "$BASE_URL/members/$id" \
    -H "Authorization: Bearer $token")
  echo "$response" | jq .
}

create_cv_app() {
  local json_file=$1

  local fullName
  fullName=$(jq -r '.fullName' "$json_file")
  local phone
  phone=$(jq -r '.phone' "$json_file")
  local linkedIn
  linkedIn=$(jq -r '.linkedIn' "$json_file")
  local gitHub
  gitHub=$(jq -r '.gitHub' "$json_file")
  local course
  course=$(jq -r '.course' "$json_file")
  local period
  period=$(jq -r '.period' "$json_file")
  local resume
  resume=$(jq -r '.resume' "$json_file")

  if [ ! -f "$resume" ]; then
    echo "Resume file '$resume' not found. Aborting."
    exit 1
  fi

  local response
  response=$(curl -s -X POST "$BASE_URL/cv-applications" \
    -F "fullName=$fullName" \
    -F "phone=$phone" \
    -F "linkedIn=$linkedIn" \
    -F "gitHub=$gitHub" \
    -F "course=$course" \
    -F "period=$period" \
    -F "resume=@$resume")

  echo "$response" | jq .
}

get_cv_apps() {
  local token
  token=$(cat "$TOKEN_FILE" 2>/dev/null)
  local response
  response=$(curl -s "$BASE_URL/cv-applications" \
    -H "Authorization: Bearer $token")
  echo "$response" | jq .
}

get_cv_resume() {
  local filename=$1
  curl -s "$BASE_URL/cv-applications/resume/$filename" --output "$filename"
  echo "File downloaded: $filename"
}

create_proposal() {
  local json_file=$1
  local response
  response=$(curl -s -X POST "$BASE_URL/project-proposals" \
    -H "Content-Type: application/json" \
    -d @"$json_file")
  echo "$response" | jq .
}

get_proposals() {
  local token
  token=$(cat "$TOKEN_FILE" 2>/dev/null)
  local response
  response=$(curl -s "$BASE_URL/project-proposals" \
    -H "Authorization: Bearer $token")
  echo "$response" | jq .
}

case "$1" in
  login)
    login "$2" "$3"
    ;;
  register)
    register_user "$2" "$3"
    ;;
  get-users)
    get_users
    ;;
  get-members)
    get_members
    ;;
  get-member)
    get_member "$2"
    ;;
  create-member)
    create_member "$2"
    ;;
  update-member)
    update_member "$2" "$3"
    ;;
  delete-member)
    delete_member "$2"
    ;;
  create-cv-app)
    create_cv_app "$2"
    ;;
  get-cv-apps)
    get_cv_apps
    ;;
  get-cv-resume)
    get_cv_resume "$2"
    ;;
  create-proposal)
    create_proposal "$2"
    ;;
  get-proposals)
    get_proposals
    ;;
  *)
    usage
    ;;
esac
