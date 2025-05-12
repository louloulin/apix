package com.louloulin.apix.edge.cicd

/**
 * CI/CD 流水线类型枚举
 * 定义了支持的 CI/CD 流水线类型
 * 实现 plan7.md 中的 4.2.1 节"CI/CD 流水线"功能
 */
enum class PipelineType {
    /**
     * Jenkins 流水线
     */
    JENKINS,
    
    /**
     * GitLab CI/CD 流水线
     */
    GITLAB,
    
    /**
     * GitHub Actions 流水线
     */
    GITHUB_ACTIONS,
    
    /**
     * CircleCI 流水线
     */
    CIRCLE_CI,
    
    /**
     * Travis CI 流水线
     */
    TRAVIS_CI,
    
    /**
     * TeamCity 流水线
     */
    TEAM_CITY,
    
    /**
     * Bamboo 流水线
     */
    BAMBOO,
    
    /**
     * Azure DevOps 流水线
     */
    AZURE_DEVOPS,
    
    /**
     * 自定义流水线
     */
    CUSTOM
}
