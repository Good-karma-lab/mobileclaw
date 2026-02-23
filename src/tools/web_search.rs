//! Web Search Tool using Brave Search API
//!
//! Allows the agent to search the web for current information

use async_trait::async_trait;
use reqwest::Client;
use serde::Deserialize;

use crate::tools::traits::{Tool, ToolResult};

pub struct WebSearchTool {
    api_key: Option<String>,
    client: Client,
}

impl WebSearchTool {
    pub fn new(api_key: Option<String>) -> Self {
        let client = Client::builder()
            .timeout(std::time::Duration::from_secs(30))
            .user_agent("ZeroClaw/1.0")
            .build()
            .unwrap_or_else(|_| Client::new());

        Self { api_key, client }
    }
}

#[derive(Debug, Deserialize)]
struct WebSearchRequest {
    query: String,
    #[serde(default = "default_count")]
    count: usize,
}

fn default_count() -> usize {
    5
}

#[derive(Debug, Clone)]
struct SearchResult {
    title: String,
    url: String,
    description: String,
}

#[derive(Debug, Deserialize)]
struct BraveSearchResponse {
    web: Option<WebResults>,
}

#[derive(Debug, Deserialize)]
struct WebResults {
    results: Vec<BraveResult>,
}

#[derive(Debug, Deserialize)]
struct BraveResult {
    title: String,
    url: String,
    description: Option<String>,
}

#[async_trait]
impl Tool for WebSearchTool {
    fn name(&self) -> &str {
        "web_search"
    }

    fn description(&self) -> &str {
        "Search the web for current information. Returns relevant search results with titles, URLs, and descriptions. Useful for finding recent news, looking up facts, or researching topics."
    }

    fn parameters_schema(&self) -> serde_json::Value {
        serde_json::json!({
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "The search query"
                },
                "count": {
                    "type": "integer",
                    "description": "Number of results to return (default: 5, max: 10)",
                    "default": 5
                }
            },
            "required": ["query"]
        })
    }

    async fn execute(&self, args: serde_json::Value) -> anyhow::Result<ToolResult> {
        let req: WebSearchRequest = serde_json::from_value(args)?;

        if req.query.trim().is_empty() {
            return Ok(ToolResult {
                success: false,
                output: String::new(),
                error: Some("Query cannot be empty".into()),
            });
        }

        let count = req.count.clamp(1, 10);

        let results = if let Some(ref api_key) = &self.api_key {
            self.search_brave(&req.query, count, api_key).await?
        } else {
            self.search_duckduckgo(&req.query, count).await?
        };

        let output = results
            .iter()
            .enumerate()
            .map(|(i, r)| {
                format!(
                    "{}. **{}**\n   {}\n   <{}>",
                    i + 1,
                    r.title,
                    if r.description.is_empty() { "No description" } else { &r.description },
                    r.url
                )
            })
            .collect::<Vec<_>>()
            .join("\n\n");

        Ok(ToolResult {
            success: true,
            output: format!("Found {} results:\n\n{}", results.len(), output),
            error: None,
        })
    }
}

impl WebSearchTool {
    async fn search_brave(
        &self,
        query: &str,
        count: usize,
        api_key: &str,
    ) -> anyhow::Result<Vec<SearchResult>> {
        let url = format!(
            "https://api.search.brave.com/res/v1/web/search?q={}&count={}",
            urlencoding::encode(query),
            count
        );

        let response = self
            .client
            .get(&url)
            .header("X-Subscription-Token", api_key)
            .header("Accept", "application/json")
            .send()
            .await?;

        if !response.status().is_success() {
            anyhow::bail!("Brave Search API error: {}", response.status());
        }

        let brave_response: BraveSearchResponse = response.json().await?;

        let results = brave_response
            .web
            .map(|w| w.results)
            .unwrap_or_default()
            .into_iter()
            .filter_map(|r| {
                Some(SearchResult {
                    title: r.title,
                    url: r.url,
                    description: r.description.unwrap_or_default(),
                })
            })
            .take(count)
            .collect();

        Ok(results)
    }

    async fn search_duckduckgo(
        &self,
        query: &str,
        count: usize,
    ) -> anyhow::Result<Vec<SearchResult>> {
        let url = format!(
            "https://api.duckduckgo.com/?q={}&format=json&no_html=1",
            urlencoding::encode(query)
        );

        let response = self.client.get(&url).send().await?;

        if !response.status().is_success() {
            anyhow::bail!("DuckDuckGo API error: {}", response.status());
        }

        let ddg_response: serde_json::Value = response.json().await?;

        let mut results = Vec::new();

        // Extract abstract text
        if let Some(abstract_text) = ddg_response.get("AbstractText").and_then(|v| v.as_str()) {
            if !abstract_text.is_empty() {
                results.push(SearchResult {
                    title: "Summary".into(),
                    url: format!("https://duckduckgo.com/?q={}", urlencoding::encode(query)),
                    description: abstract_text.to_string(),
                });
            }
        }

        // Extract related topics
        if let Some(related) = ddg_response.get("RelatedTopics").and_then(|v| v.as_array()) {
            for topic in related.iter().take(count.saturating_sub(results.len())) {
                if let Some(obj) = topic.as_object() {
                    if let Some(text) = obj.get("Text").and_then(|v| v.as_str()) {
                        if let Some(first_url) = obj.get("FirstURL").and_then(|v| v.as_str()) {
                            results.push(SearchResult {
                                title: text.to_string(),
                                url: first_url.to_string(),
                                description: String::new(),
                            });
                        }
                    }
                }
            }
        }

        // If no results, add a web search hint
        if results.is_empty() {
            results.push(SearchResult {
                title: "Web Search".into(),
                url: format!("https://duckduckgo.com/?q={}", urlencoding::encode(query)),
                description: format!(
                    "Configure a Brave Search API key in settings for better results. Query: {}",
                    query
                ),
            });
        }

        Ok(results.into_iter().take(count).collect())
    }
}
