package com.louloulin.apix.edge.deploy.cloud

/**
 * 云提供商类型枚举
 * 定义了支持的云提供商类型
 * 实现 plan7.md 中的 4.1.3 节"云原生部署"功能
 */
enum class CloudProviderType {
    /**
     * 亚马逊云服务
     */
    AWS,
    
    /**
     * 谷歌云平台
     */
    GCP,
    
    /**
     * 微软 Azure
     */
    AZURE,
    
    /**
     * 阿里云
     */
    ALIYUN,
    
    /**
     * 腾讯云
     */
    TENCENT_CLOUD,
    
    /**
     * 华为云
     */
    HUAWEI_CLOUD,
    
    /**
     * 百度云
     */
    BAIDU_CLOUD,
    
    /**
     * 自定义云提供商
     */
    CUSTOM
}
