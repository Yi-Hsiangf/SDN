# ONOS-APP-for-DHCP-unicast

## Background
* Originally, DHCP is in broadcast
* In this case, I do some improvement
  * DHCP DISCOVER will be packet-in to controller by switch
  * Controller compute path between DHCP client and server
  * Controller install flow rules to forward DHCP packets

## Compile and Install the application
```bash
$ cd $ONOS_ROOT/tools/package/archetypes/app
$ mvn clean install -DskipTests
$ onos-app localhost reinstall! target/app-1.0-SNAPSHOT.oar
```

## Start the topology
```bash
$ sudo python topo.py
```

## Use following command to upload a config file by REST API
```bash
$ onos-netcfg {controller_IP} {json_file_name}
```
* For example: onos-netcfg 127.0.0.1 unicastdhcp.json

## In mininet CLI, use following command to ask an IP for a host 
```bash
mininet> h1 dhclient {interface_name}
```
* For example: h1 dhclient h1-eth0
