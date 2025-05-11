# Testing CRD Implementation

This document describes how to test the CRD implementation for APIX.

## Custom Resource Definitions (CRDs)

The following CRDs have been implemented:

- `gateway-crd.yaml`: Defines the Gateway resource for APIX
- `route-crd.yaml`: Defines the Route resource for APIX
- `plugin-crd.yaml`: Defines the Plugin resource for APIX

## K8sDeployManager Implementation

The `K8sDeployManager` class has been enhanced to support CRD management with the following methods:

- `loadCRDs()`: Loads CRD configurations from the deployment configuration
- `getCRDs()`: Returns a list of all CRDs
- `getCRDDetails(crdId)`: Returns details of a specific CRD
- `installCRD(clusterId, crdId)`: Installs a CRD to a cluster
- `uninstallCRD(installId)`: Uninstalls a CRD from a cluster
- `getCRDInstallStatus(installId)`: Returns the installation status of a CRD
- `getAllCRDInstallStatus()`: Returns the installation status of all CRDs

## Manual Testing

To manually test the CRD implementation:

1. Install the CRDs:

```bash
kubectl apply -f k8s/crds/
```

2. Verify the CRDs are installed:

```bash
kubectl get crds | grep apix.louloulin.com
```

3. Create a Gateway resource:

```yaml
apiVersion: apix.louloulin.com/v1
kind: Gateway
metadata:
  name: example-gateway
spec:
  mode: hybrid
  replicas: 1
  config:
    logLevel: info
    adminPort: 8001
    proxyPort: 8000
    dbless: true
    eventBus:
      clusterName: apix-cluster
      clusterType: hazelcast
```

4. Create a Route resource:

```yaml
apiVersion: apix.louloulin.com/v1
kind: Route
metadata:
  name: example-route
spec:
  hosts:
    - api.example.com
  paths:
    - /api
  methods:
    - GET
    - POST
  upstream:
    targets:
      - host: backend.example.com
        port: 8080
        weight: 100
    loadBalancing: round-robin
```

5. Create a Plugin resource:

```yaml
apiVersion: apix.louloulin.com/v1
kind: Plugin
metadata:
  name: rate-limiting
spec:
  name: rate-limiting
  enabled: true
  global: true
  config:
    limit: 100
    period: minute
```

## Automated Testing

The `K8sDeployManagerTest` class includes tests for the CRD management functionality:

- `testGetCRDs`: Tests retrieving the list of CRDs
- `testGetCRDDetails`: Tests retrieving details of a specific CRD
- `testInstallCRD`: Tests installing a CRD to a cluster
- `testUninstallCRD`: Tests uninstalling a CRD from a cluster
- `testGetAllCRDInstallStatus`: Tests retrieving the installation status of all CRDs

## Helm Chart and Kustomize

The Helm chart and Kustomize configurations have been implemented to support the CRDs:

- Helm chart: `helm/apix/`
- Kustomize: `k8s/kustomize/`

## Kubernetes Operator

The Kubernetes Operator has been implemented to manage APIX resources:

- Operator deployment: `k8s/operator/deploy/`
- RBAC resources: `k8s/operator/rbac/`
