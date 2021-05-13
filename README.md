<p align="center">
  <img src="event-hubs.png" alt="Microsoft Azure Event Hubs" width="100"/>
</p>

<h1 align="center">Extensions on <a href="https://github.com/apache/qpid-proton-j">qpid-proton-j library</a>
  <p align="center">
    <a href="#star-our-repo">
      <img src="https://img.shields.io/github/stars/azure/qpid-proton-j-extensions.svg?style=social&label=Stars"
        alt="star our repo"></a>
    <a href="https://twitter.com/intent/follow?screen_name=azure">
      <img src="https://img.shields.io/twitter/url/http/shields.io.svg?style=social&label=Follow%20@azure"
        alt="follow on Twitter"></a>
  </p>
</h1>

The Advanced Message Queuing Protocol (AMQP) is an open internet protocol for messaging. Several messaging platforms
hosted on Azure like Azure Service Bus, Azure Event Hubs and Azure IOT Hub supports AMQP 1.0. To know more about AMQP
refer to the [OASIS AMQP 1.0 protocol specification][amqp-spec].

In our Java client libraries we use [qpid-proton-j library][proton-j], an open source implementation of AMQP. Extensions
built atop [qpid-proton-j library][proton-j] are hosted here.

## Issues

Currently, this library is used only in conjunction with [Azure IOT SDK][azure-iot] or
[Azure Event Hubs client library][azure-sdk-for-java]. File the issues directly on the respective projects.

## Contributing

This project welcomes contributions and suggestions. Please refer to our [Contribution Guidelines](./CONTRIBUTING.md)
for more details.

<!-- Links -->

[amqp-spec]: http://docs.oasis-open.org/amqp/core/v1.0/amqp-core-overview-v1.0.html
[azure-iot]: https://github.com/Azure/azure-iot-sdk-java
[azure-sdk-for-java]: https://github.com/Azure/azure-sdk-for-java
[proton-j]: https://github.com/apache/qpid-proton-j
