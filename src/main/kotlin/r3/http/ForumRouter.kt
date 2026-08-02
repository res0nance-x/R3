package r3.http
//class .ForumRouter(val path: String, val repo: DirRepo) : IRouter {
//	override fun findRoute(session: IHTTPSession): Response? {
//		if (!session.uri.startsWith(path)) {
//			return null
//		}
//		val forumPath = session.uri.substring(path.length)
//		val pathArr = forumPath.split('/')
//
//		val (topic, name) = when (pathArr.size) {
//			0 -> listOf("", "")
//			1 -> listOf(pathArr[0], "")
//			2 -> listOf(pathArr[0], pathArr[1])
//			else -> return null
//		}
//		when {
//			session.uri.let {
//				it.endsWith("/index.html") &&
//						(session.method == Method.GET || session.method == Method.HEAD)
//			} -> {
//				session.uri = "/messages.html"
//				return null
//			}
//
//			(session.method == Method.GET || session.method == Method.HEAD) -> {
//				val topicDir = repo.getTopic(topic)
//				if (topic.isEmpty()) {
//					val topicList = JSONArray()
//					for (t in repo.listTopicFiles()) {
//						topicList.put(t.name)
//					}
//					return Response.newFixedLengthResponse(Status.OK, "text/plain", topicList.toString())
//				}
//				val clientState = session.parameters["state"]?.firstOrNull()
//				if (!clientState.isNullOrEmpty()) {
//					val stateKey = Key256(clientState)
//					repo.waitForUpdate(topicDir, stateKey)
//				}
//
//				if (name.isEmpty()) {
//					val (state, list) = repo.calculateState(topicDir)
//					val json = JSONObject()
//					val jsonList = JSONArray()
//					for (f in list) {
//						jsonList.put(f.name)
//					}
//					json.put("state", state)
//					json.put("files", jsonList)
//					return Response.newFixedLengthResponse(Status.OK, "text/plain", json.toString())
//				}
//				val file = File(File(repo.contentDir, topic), name)
//				if (file.exists()) {
//					return rangeRequestResponse(session, FileContent(file))
//				}
//			}
//
//			session.method == Method.PUT -> {
//				try {
//					val inLength = (session.headers["content-length"] ?: "-1").toLong()
//					repo.addFile(
//						File(repo.getTopic(topic), name),
//						BoundedInputStream(session.inputStream, inLength)
//					)
//					val r = Response.newFixedLengthResponse(
//						Status.CREATED,
//						"text/plain",
//						"Success"
//					)
//					r.addHeader("Location", path)
//					return r
//				} catch (e: Exception) {
//					e.printStackTrace()
//				}
//			}
//
//			session.method == Method.DELETE -> {
//				File(File(repo.contentDir, topic), name).delete()
//				return Response.newFixedLengthResponse(Status.OK, "text/plain", "true")
//			}
//		}
//		return null
//	}
//}