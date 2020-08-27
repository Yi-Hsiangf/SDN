from mininet.topo import Topo
from mininet.cli import CLI

host_num = 0
link_num = 0

class MyTopo(Topo):
	def __init__(self):
		Topo.__init__(self)
		
		global host_num
		global link_num
		# Add host
		h1 = self.addHost("h1")
		h2 = self.addHost("h2")
		h3 = self.addHost("h3")
		h4 = self.addHost("h4")
		
		host_num = 4
		
		# Add switch
		s1 = self.addSwitch("s1")
		s2 = self.addSwitch("s2")
		s3 = self.addSwitch("s3")
		s4 = self.addSwitch("s4")
		
		# Add links
		self.addLink(h1,s1)
		self.addLink(h2,s1)
		
		self.addLink(h3,s2)
		self.addLink(h4,s2)
		
		self.addLink(s1,s3)
		self.addLink(s1,s4)
		
		self.addLink(s2,s3)
		self.addLink(s2,s4)
		
		link_num = 8;
		
		
topos = {"mytopo" : MyTopo}

def addHostToSwitch( self, line ):
	"mycmd is an example command to extend the Mininet CLI"
	net = self.mn
	switchname = line
	
	#global
	global host_num
	global link_num
	
	#count host number
	host_num += 1
	str_host_num = str(host_num)
	
	#add host
	net.addHost('h' + str_host_num)
	
	#add link
	net.addLink(switchname, net.get('h'+str_host_num))
	
	#get switch and attach interface to switch
	link_num += 1;
	
	str_link_num = str(link_num)
	
	switch = net.get(switchname)
	switch.attach( switch + "-eth" + str(len(switch.intfList())-1))
	
	#set host ip
	net.get('h'+str_host_num).setIP("10.0.0." + str_host_num)
	
	
CLI.do_addHostToSwitch = addHostToSwitch