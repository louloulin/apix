# APIX Kubernetes Integration

This directory contains Kubernetes integration resources for APIX AI Gateway, including:

- Custom Resource Definitions (CRDs)
- Kubernetes Operator
- Helm Chart
- Kustomize configurations

## Custom Resource Definitions (CRDs)

The `crds` directory contains the Custom Resource Definitions for APIX:

- `gateway-crd.yaml`: Defines the Gateway resource for APIX
- `route-crd.yaml`: Defines the Route resource for APIX
- `plugin-crd.yaml`: Defines the Plugin resource for APIX

To install the CRDs:

```bash
kubectl apply -f crds/
```

## Kubernetes Operator

The `operator` directory contains the APIX Operator, which automates the deployment and management of APIX resources.

To install the operator:

```bash
# Create the namespace
kubectl create namespace apix-system

# Install RBAC resources
kubectl apply -f operator/rbac/

# Install the operator
kubectl apply -f operator/deploy/
```

## Helm Chart

The Helm chart for APIX is located in the `../helm/apix` directory.

To install APIX using Helm:

```bash
# Add the APIX Helm repository
helm repo add apix https://louloulin.github.io/apix/charts
helm repo update

# Install APIX
helm install apix apix/apix
```

Or, to install from the local chart:

```bash
helm install apix ../helm/apix
```

## Kustomize

The `kustomize` directory contains Kustomize configurations for different deployment scenarios:

- `base`: Base configuration
- `overlays/dev`: Development environment
- `overlays/prod`: Production environment
- `overlays/edge`: Edge deployment

To deploy APIX using Kustomize:

```bash
# Development environment
kubectl apply -k kustomize/overlays/dev

# Production environment
kubectl apply -k kustomize/overlays/prod

# Edge deployment
kubectl apply -k kustomize/overlays/edge
```

## Examples

The `examples` directory contains example resources for APIX:

- Gateway examples
- Route examples
- Plugin examples

## Documentation

For more detailed documentation, please refer to the [APIX Documentation](https://github.com/louloulinlv/apix/docs).
