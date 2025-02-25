terraform {
  required_version = ">= 1.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    null = {
      source  = "hashicorp/null"
      version = "~> 3.0"
    }
  }
}

# First create API Gateway
# resource "aws_apigatewayv2_api" "main" {
#   name          = "${var.project_name}-api-${var.environment}"
#   protocol_type = "HTTP"
#
#   cors_configuration {
#     allow_origins = ["https://${var.frontend_domain}"]
#     allow_methods = ["GET", "POST", "PUT", "DELETE"]
#     allow_headers = ["*"]
#   }
# }

resource "aws_apigatewayv2_api" "main" {
  name          = "${var.project_name}-api-${var.environment}"
  protocol_type = "HTTP"

  cors_configuration {
    allow_origins  = ["https://${var.frontend_domain}"]
    allow_methods  = ["POST", "OPTIONS"]
    allow_headers  = ["content-type", "authorization"]
    expose_headers = ["*"]
    max_age        = 300
  }

  body = jsonencode({
    openapi = "3.0.1"
    info = {
      title   = "${var.project_name}-api-${var.environment}"
      version = "1.0"
    }
    paths = {
      "/graphql" = {
        post = {
          responses = {
            "200" = {
              description = "Success"
              content = {
                "application/json" = {
                  schema = {
                    type = "object"
                  }
                }
              }
            }
          }
          x-amazon-apigateway-integration = {
            payloadFormatVersion = "2.0"
            type                 = "AWS_PROXY"
            httpMethod           = "POST"
            uri                  = aws_lambda_function.graphql.invoke_arn
            connectionType       = "INTERNET"
          }
        }
        options = {
          responses = {
            "200" = {
              description = "CORS support"
              headers = {
                "Access-Control-Allow-Origin" = {
                  schema = { type = "string" }
                }
                "Access-Control-Allow-Methods" = {
                  schema = { type = "string" }
                }
                "Access-Control-Allow-Headers" = {
                  schema = { type = "string" }
                }
              }
            }
          }
          x-amazon-apigateway-integration = {
            type = "MOCK"
            requestTemplates = {
              "application/json" = "{\"statusCode\": 200}"
            }
            responses = {
              default = {
                statusCode = "200"
                responseParameters = {
                  "method.response.header.Access-Control-Allow-Methods" = "'POST,OPTIONS'"
                  "method.response.header.Access-Control-Allow-Headers" = "'content-type,authorization'"
                  "method.response.header.Access-Control-Allow-Origin"  = "'https://${var.frontend_domain}'"
                }
                responseTemplates = {
                  "application/json" = "{}"
                }
              }
            }
          }
        }
      }
    }
  })
}

# Create CloudWatch log groups before Lambda functions
# resource "aws_cloudwatch_log_group" "lambda_tides" {
#   name              = "/aws/lambda/${var.project_name}-tides-${var.environment}"
#   retention_in_days = var.log_retention_days
# }
#
# resource "aws_cloudwatch_log_group" "lambda_stations" {
#   name              = "/aws/lambda/${var.project_name}-stations-${var.environment}"
#   retention_in_days = var.log_retention_days
# }

resource "aws_cloudwatch_log_group" "lambda_graphql" {
  name              = "/aws/lambda/${var.project_name}-graphql-${var.environment}"
  retention_in_days = var.log_retention_days
}

resource "aws_cloudwatch_log_group" "api_logs" {
  name              = "/aws/apigateway/${var.project_name}-${var.environment}"
  retention_in_days = var.log_retention_days
}

locals {
  dummy_zip_path = "${path.module}/dummy.zip"
}

resource "null_resource" "dummy_zip" {
  provisioner "local-exec" {
    command = <<-EOT
      echo "dummy" > ${path.module}/dummy.txt
      zip ${local.dummy_zip_path} ${path.module}/dummy.txt
      rm ${path.module}/dummy.txt
    EOT
  }
}

# Create Lambda functions
# resource "aws_lambda_function" "tides" {
#   filename         = var.lambda_jar_path != null ? var.lambda_jar_path : local.dummy_zip_path
#   source_code_hash = var.lambda_jar_hash
#   function_name    = "${var.project_name}-tides-${var.environment}"
#   role             = var.lambda_role_arn
#   handler          = "bootstrap"    # Change to Go bootstrap handler
#   runtime          = "provided.al2" # Change to AL2 runtime for Go
#   memory_size      = var.lambda_memory_size
#   timeout          = var.lambda_timeout
#   publish          = var.lambda_publish_version
#   architectures    = ["arm64"] # Add ARM64 architecture for Go
#
#   environment {
#     variables = {
#       STATION_LIST_BUCKET         = var.station_list_bucket_id
#       ALLOWED_ORIGINS             = "https://${var.frontend_domain}"
#       LOG_LEVEL                   = "DEBUG"
#       CACHE_TIDE_LRU_SIZE         = tostring(var.cache_lru_size)
#       CACHE_TIDE_LRU_TTL_MINUTES  = tostring(var.cache_lru_ttl_minutes)
#       CACHE_DYNAMO_TTL_DAYS       = tostring(var.cache_dynamo_ttl_days)
#       CACHE_STATION_LIST_TTL_DAYS = tostring(var.cache_station_list_ttl_days)
#       CACHE_BATCH_SIZE            = tostring(var.cache_batch_size)
#       CACHE_MAX_BATCH_RETRIES     = tostring(var.cache_max_batch_retries)
#       CACHE_ENABLE_LRU            = tostring(var.cache_enable_lru)
#       CACHE_ENABLE_DYNAMO         = tostring(var.cache_enable_dynamo)
#     }
#   }
#
#   depends_on = [
#     aws_cloudwatch_log_group.lambda_tides,
#     null_resource.dummy_zip
#   ]
#
#   lifecycle {
#     ignore_changes = [
#       filename,
#       source_code_hash,
#       publish,
#     ]
#   }
# }

# resource "aws_lambda_function" "stations" {
#   filename         = var.lambda_jar_path != null ? var.lambda_jar_path : local.dummy_zip_path
#   source_code_hash = var.lambda_jar_hash
#   function_name    = "${var.project_name}-stations-${var.environment}"
#   role             = var.lambda_role_arn
#   handler          = "bootstrap"    # Change to Go bootstrap handler
#   runtime          = "provided.al2" # Change to AL2 runtime for Go
#   memory_size      = var.lambda_memory_size
#   timeout          = var.lambda_timeout
#   publish          = var.lambda_publish_version
#   architectures    = ["arm64"] # Add ARM64 architecture for Go
#
#   environment {
#     variables = {
#       STATION_LIST_BUCKET         = var.station_list_bucket_id
#       ALLOWED_ORIGINS             = "https://${var.frontend_domain}"
#       LOG_LEVEL                   = "DEBUG"
#       CACHE_TIDE_LRU_SIZE         = tostring(var.cache_lru_size)
#       CACHE_TIDE_LRU_TTL_MINUTES  = tostring(var.cache_lru_ttl_minutes)
#       CACHE_DYNAMO_TTL_DAYS       = tostring(var.cache_dynamo_ttl_days)
#       CACHE_STATION_LIST_TTL_DAYS = tostring(var.cache_station_list_ttl_days)
#       CACHE_BATCH_SIZE            = tostring(var.cache_batch_size)
#       CACHE_MAX_BATCH_RETRIES     = tostring(var.cache_max_batch_retries)
#       CACHE_ENABLE_LRU            = tostring(var.cache_enable_lru)
#       CACHE_ENABLE_DYNAMO         = tostring(var.cache_enable_dynamo)
#     }
#   }
#
#   depends_on = [
#     aws_cloudwatch_log_group.lambda_stations,
#     null_resource.dummy_zip
#   ]
#
#   lifecycle {
#     ignore_changes = [
#       filename,
#       source_code_hash,
#       publish,
#     ]
#   }
# }

resource "aws_lambda_function" "graphql" {
  filename         = var.lambda_jar_path != null ? var.lambda_jar_path : local.dummy_zip_path
  source_code_hash = var.lambda_jar_hash
  function_name    = "${var.project_name}-graphql-${var.environment}"
  role             = var.lambda_role_arn
  handler          = "bootstrap"
  runtime          = "provided.al2"
  memory_size      = var.lambda_memory_size
  timeout          = var.lambda_timeout
  publish          = var.lambda_publish_version
  architectures    = ["arm64"] # Add ARM64 architecture for Go

  environment {
    variables = {
      STATION_LIST_BUCKET           = var.station_list_bucket_id
      ALLOWED_ORIGINS               = "https://${var.frontend_domain}"
      LOG_LEVEL                     = "DEBUG"
      CACHE_TIDE_LRU_SIZE           = tostring(var.cache_lru_size)
      CACHE_TIDE_LRU_TTL_MINUTES    = tostring(var.cache_lru_ttl_minutes)
      CACHE_GRAPHQL_LRU_SIZE        = tostring(var.cache_graphql_lru_size)
      CACHE_GRAPHQL_LRU_TTL_MINUTES = tostring(var.cache_graphql_lru_ttl_minutes)
      CACHE_DYNAMO_TTL_DAYS         = tostring(var.cache_dynamo_ttl_days)
      CACHE_STATION_LIST_TTL_DAYS   = tostring(var.cache_station_list_ttl_days)
      CACHE_BATCH_SIZE              = tostring(var.cache_batch_size)
      CACHE_MAX_BATCH_RETRIES       = tostring(var.cache_max_batch_retries)
      CACHE_ENABLE_LRU              = tostring(var.cache_enable_lru)
      CACHE_ENABLE_DYNAMO           = tostring(var.cache_enable_dynamo)
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.lambda_graphql,
    null_resource.dummy_zip
  ]

  lifecycle {
    ignore_changes = [
      filename,
      source_code_hash,
      publish,
    ]
  }
}

# Create API Gateway integrations
# resource "aws_apigatewayv2_integration" "tides" {
#   api_id             = aws_apigatewayv2_api.main.id
#   integration_type   = "AWS_PROXY"
#   integration_method = "POST"
#   integration_uri    = aws_lambda_function.tides.invoke_arn
#
#   depends_on = [aws_lambda_function.tides]
# }
#
# resource "aws_apigatewayv2_integration" "stations" {
#   api_id             = aws_apigatewayv2_api.main.id
#   integration_type   = "AWS_PROXY"
#   integration_method = "POST"
#   integration_uri    = aws_lambda_function.stations.invoke_arn
#
#   depends_on = [aws_lambda_function.stations]
# }

resource "aws_apigatewayv2_integration" "graphql" {
  api_id             = aws_apigatewayv2_api.main.id
  integration_type   = "AWS_PROXY"
  integration_method = "POST"
  integration_uri    = aws_lambda_function.graphql.invoke_arn

  depends_on = [aws_lambda_function.graphql]
}

# Create routes
# resource "aws_apigatewayv2_route" "tides" {
#   api_id    = aws_apigatewayv2_api.main.id
#   route_key = "GET /api/tides"
#   target    = "integrations/${aws_apigatewayv2_integration.tides.id}"
#
#   depends_on = [aws_apigatewayv2_integration.tides]
# }
#
# resource "aws_apigatewayv2_route" "stations" {
#   api_id    = aws_apigatewayv2_api.main.id
#   route_key = "GET /api/stations"
#   target    = "integrations/${aws_apigatewayv2_integration.stations.id}"
#
#   depends_on = [aws_apigatewayv2_integration.stations]
# }

# resource "aws_apigatewayv2_route" "graphql" {
#   api_id    = aws_apigatewayv2_api.main.id
#   route_key = "POST /graphql"
#   target    = "integrations/${aws_apigatewayv2_integration.graphql.id}"
#
#   depends_on = [aws_apigatewayv2_integration.graphql]
# }

# Create API Gateway stage
resource "aws_apigatewayv2_stage" "main" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = "$default"
  auto_deploy = true

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_logs.arn
    format = jsonencode({
      requestId      = "$context.requestId"
      ip             = "$context.identity.sourceIp"
      requestTime    = "$context.requestTime"
      httpMethod     = "$context.httpMethod"
      resourcePath   = "$context.resourcePath"
      status         = "$context.status"
      protocol       = "$context.protocol"
      responseLength = "$context.responseLength"
      graphqlErrors  = "$context.error.message"
    })
  }

  depends_on = [aws_cloudwatch_log_group.api_logs]
}

# Create Lambda permissions
# resource "aws_lambda_permission" "api_gw_tides" {
#   statement_id  = "AllowAPIGatewayInvoke"
#   action        = "lambda:InvokeFunction"
#   function_name = aws_lambda_function.tides.function_name
#   principal     = "apigateway.amazonaws.com"
#   source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
# }
#
# resource "aws_lambda_permission" "api_gw_stations" {
#   statement_id  = "AllowAPIGatewayInvoke"
#   action        = "lambda:InvokeFunction"
#   function_name = aws_lambda_function.stations.function_name
#   principal     = "apigateway.amazonaws.com"
#   source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
# }

resource "aws_lambda_permission" "api_gw_graphql" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.graphql.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
}

# Create CloudWatch alarms
resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  alarm_name          = "${var.project_name}-${var.environment}-lambda-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = "300"
  statistic           = "Sum"
  threshold           = "0"
  alarm_description   = "Lambda function error rate"

  dimensions = {
    FunctionName = aws_lambda_function.graphql.function_name
  }
}
