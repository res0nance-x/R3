package r3.pack

import r3.collection.HashSimpleMap
import r3.content.Content

class RAMPack : Pack, HashSimpleMap<String, Content>()