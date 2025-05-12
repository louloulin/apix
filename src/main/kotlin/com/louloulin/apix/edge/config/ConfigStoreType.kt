package com.louloulin.apix.edge.config

/**
 * 配置存储类型枚举
 * 定义了支持的配置存储类型
 * 实现 plan7.md 中的 4.2.2 节"配置管理"功能
 */
enum class ConfigStoreType {
    /**
     * Git 配置存储
     */
    GIT,

    /**
     * GitOps 配置存储
     * 基于 GitOps 模式的配置管理，支持 PR 工作流和自动同步
     */
    GITOPS,

    /**
     * 文件系统配置存储
     */
    FILE,

    /**
     * 数据库配置存储
     */
    DATABASE,

    /**
     * Kubernetes ConfigMap 配置存储
     */
    KUBERNETES,

    /**
     * Consul 配置存储
     */
    CONSUL,

    /**
     * Etcd 配置存储
     */
    ETCD,

    /**
     * ZooKeeper 配置存储
     */
    ZOOKEEPER,

    /**
     * Redis 配置存储
     */
    REDIS,

    /**
     * 内存配置存储
     */
    MEMORY
}
