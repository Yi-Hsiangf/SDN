# ONOS_Mininet

### Create my own CLI on mininet
   
Install ONOS:
```bash
$ sudo apt-get install software-properties-common -y && \
sudo add-apt-repository ppa:webupd8team/java -y && \
sudo apt-get update && \
echo "oracle-java8-installer shared/accepted-oracle-license-v1-1 select true" | sudo debconf-set-selections && \
sudo apt-get install oracle-java8-installer oracle-java8-set-default -y
```        
Other dependencies: 
```bash
$ sudo apt-get install git zip curl unzip pyth
``` 
Install require package: 
```bash
$ sudo apt-get install pkg-config zip g++ zlib1g-dev unzip python
```
Download Bazel: 
```bash
$ wget -c https://github.com/bazelbuild/bazel/releases/download/0.22.0/bazel-0.22.0-installer-linux-x86_64.sh
```

Run the installer: 
```bash
$ chmod +x bazel-0.22.0-installer-linux-x86_64.sh
$ ./bazel-0.22.0-installer-linux-x86_64.sh --user
```                   
Add the line in ~/.bashrc : 
```bash
export PATH="$PATH:$HOME/bin"
```  
Download ONOS: 
```bash
$ cd ~/ && git clone https://gerrit.onosproject.org/onos
$ cd onos && git checkout onos-1.15
```  
Build ONOS: 
```bash
$ bazel build onos
```  
Run ONOS: 
```bash
$ bazel run onos-local -- clean deb
```
Install Mininet :
```bash
$ cd ~/ && git clone https://github.com/mininet/mininet
$ cd mininet 
$ util/install.sh -a
```
Login into ONOS CLI: 
```bash
$ cd ~/onos
$ tools/test/bin/onos localhost
```
 
Activate applications by ONOS CLI : 
```bash
onos> app activate org.onosproject.openflow
onos> app activate org.onosproject.fwd
```

### Summit own python script to mininet: 
```bash
$ sudo mn --custom=sample.py --topo=mytopo --controller=remote,ip=127.0.0.1,port=6653
```
### Visit http://localhost:8181/onos/ui
look for the topology.

(user/password : onos/rocks)
   
### Add my own CLI (AddHostToSwitch) on mininet. 
```bash
mininet> addHostToSwitch s4
```
