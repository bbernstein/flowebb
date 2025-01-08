#!/bin/bash

GITHUB_ORG="bbernstein"
GITHUB_REPO="flowebb"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query "Account" --output text)
REGION="us-east-1"

# Create OIDC Provider if it doesn't exist
aws iam create-open-id-connect-provider \
  --url "https://token.actions.githubusercontent.com" \
  --client-id-list "sts.amazonaws.com" \
  --thumbprint-list "6938fd4d98bab03faadb97b34396831e3780aea1"

# Create IAM role
aws iam create-role \
  --role-name flowebb-github-actions-prod \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [
      {
        "Effect": "Allow",
        "Principal": {
          "Federated": "arn:aws:iam::'$AWS_ACCOUNT_ID':oidc-provider/token.actions.githubusercontent.com"
        },
        "Action": "sts:AssumeRoleWithWebIdentity",
        "Condition": {
          "StringLike": {
            "token.actions.githubusercontent.com:sub": "repo:'$GITHUB_ORG'/'$GITHUB_REPO':*"
          }
        }
      }
    ]
  }'
