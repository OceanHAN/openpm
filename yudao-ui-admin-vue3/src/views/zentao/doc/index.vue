<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="文档是「库 → 章节 → 文档 → 版本」四层"
      description="zt_doc 一张表两种对象：type=chapter 是章节（目录节点，没有正文），其余类型才是文档。正文按 (doc, version) 存在 zt_doccontent，编辑正文才会产生新版本；草稿存在 version=0 的草稿位，点「发布」才升成 v1。"
    />

    <el-form class="-mb-15px" :inline="true" label-width="86px">
      <el-form-item label="文档库">
        <el-select
          v-model="queryParams.lib"
          class="!w-220px"
          clearable
          filterable
          placeholder="全部文档库"
          @change="handleQuery"
        >
          <el-option
            v-for="lib in libList"
            :key="lib.id"
            :label="`${lib.name}（${lib.typeName}）`"
            :value="lib.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="标题">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入标题关键词"
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="queryParams.type" class="!w-140px" clearable placeholder="全部">
          <el-option
            v-for="o in DOC_TYPE_OPTIONS.filter((x) => !x.chapter)"
            :key="o.value"
            :label="o.label"
            :value="o.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" class="!w-140px" clearable placeholder="全部">
          <el-option v-for="o in DOC_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:doc:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新建文档
        </el-button>
        <el-button plain @click="openForm('create', undefined, 'chapter')" v-hasPermi="['zentao:doc:create']">
          <Icon icon="ep:folder-add" class="mr-5px" /> 新建章节
        </el-button>
        <el-button plain @click="openLibDialog" v-hasPermi="['zentao:doc:create']">
          <Icon icon="ep:folder-opened" class="mr-5px" /> 文档库
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <template #header>
      <span>{{ currentLibName || '全部文档' }}</span>
      <el-tag v-if="queryParams.lib" class="ml-8px" type="info">
        {{ chapterTree.length }} 个一级章节
      </el-tag>
    </template>

    <el-table v-loading="loading" :data="list" row-key="id" empty-text="没有符合条件的文档">
      <el-table-column label="标题" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <Icon :icon="iconOf(row.type)" class="mr-5px align-middle" />
          <el-link type="primary" :underline="false" @click="openDetail(row)">
            {{ row.title }}
          </el-link>
          <el-tag v-if="row.chapter" type="info" size="small" class="ml-5px">章节</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="所属章节" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.parentTitle || '（库根）' }}</template>
      </el-table-column>
      <el-table-column label="类型" align="center" width="100">
        <template #default="{ row }">{{ row.typeName }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="{ row }">
          <el-tag :type="tagOf(DOC_STATUS_OPTIONS, row.status) as any">{{ row.statusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="版本" align="center" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.version === 0" type="warning" size="small">草稿</el-tag>
          <span v-else>v{{ row.version }}</span>
        </template>
      </el-table-column>
      <el-table-column label="浏览" align="center" prop="views" width="70" />
      <el-table-column label="文档库" prop="libName" width="140" show-overflow-tooltip />
      <el-table-column label="修改人" align="center" prop="editedBy" width="100" />
      <el-table-column label="修改时间" align="center" width="170">
        <template #default="{ row }">{{ formatDate(row.editedDate) }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          <el-button link type="primary" @click="openForm('edit', row)" v-hasPermi="['zentao:doc:update']">
            编辑
          </el-button>
          <el-button
            v-if="row.status === 'draft'"
            link
            type="success"
            @click="handlePublish(row)"
            v-hasPermi="['zentao:doc:update']"
          >
            发布
          </el-button>
          <el-button link type="primary" @click="openMove(row)" v-hasPermi="['zentao:doc:update']">移动</el-button>
          <el-button link type="danger" @click="handleDelete(row)" v-hasPermi="['zentao:doc:delete']">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 左侧章节树：点节点过滤该章节下的文档 -->
  <ContentWrap v-if="queryParams.lib">
    <template #header>章节树（点章节看它下面的文档）</template>
    <el-tree
      :data="chapterTree"
      :props="{ label: 'title', children: 'children' }"
      node-key="id"
      default-expand-all
      @node-click="(node: DocApi.DocVO) => filterByChapter(node)"
    >
      <template #default="{ data }">
        <span>
          <Icon icon="ep:folder" class="mr-5px" />
          {{ data.title }}
          <el-tag v-if="data.docCount" size="small" type="info" class="ml-5px">{{ data.docCount }}</el-tag>
        </span>
      </template>
    </el-tree>
    <el-button class="mt-10px" link type="primary" @click="queryParams.parent = undefined; handleQuery()">
      清除章节过滤
    </el-button>
  </ContentWrap>

  <DocForm ref="formRef" @success="getList" />
  <DocLibDialog ref="libDialogRef" @success="loadLibs" />

  <!-- 查看抽屉：正文 + 版本历史 -->
  <el-drawer v-model="detailVisible" :title="`文档详情 #${detail?.id ?? ''}`" size="62%">
    <div v-loading="detailLoading">
      <el-alert
        v-if="viewingVersion && viewingVersion !== detail?.version"
        type="warning"
        :closable="false"
        show-icon
        class="mb-10px"
      >
        正在查看历史版本 v{{ viewingVersion }}（当前版本为 v{{ detail?.version }}）
        <el-button link type="primary" @click="loadVersion(0)">回到当前版本</el-button>
      </el-alert>

      <el-descriptions :column="2" border class="mb-15px">
        <el-descriptions-item label="标题" :span="2">{{ detail?.title }}</el-descriptions-item>
        <el-descriptions-item label="文档库">{{ detail?.libName }}</el-descriptions-item>
        <el-descriptions-item label="上级章节">{{ detail?.parentTitle || '（库根）' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail?.typeName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="tagOf(DOC_STATUS_OPTIONS, detail?.status) as any">{{ detail?.statusName }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="当前版本">v{{ detail?.version }}</el-descriptions-item>
        <el-descriptions-item label="浏览次数">{{ detail?.views }}</el-descriptions-item>
        <el-descriptions-item label="关键词" :span="2">{{ detail?.keywords || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail?.addedBy }}</el-descriptions-item>
        <el-descriptions-item label="修改时间">{{ formatDate(detail?.editedDate) }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="正文" name="content">
          <template v-if="detail?.chapter">
            <el-empty description="章节是目录节点，没有正文" />
          </template>
          <template v-else-if="detail?.type === 'url'">
            <el-link type="primary" :href="detail?.content" target="_blank">{{ detail?.content }}</el-link>
          </template>
          <template v-else-if="detail?.type === 'markdown'">
            <pre class="doc-markdown">{{ detail?.content }}</pre>
          </template>
          <template v-else>
            <!-- 富文本按纯文本展示：本实现不引入 HTML 渲染器，避免 XSS（与操作日志的处理一致） -->
            <div class="doc-content">{{ detail?.content }}</div>
          </template>
          <el-empty v-if="!detail?.chapter && !detail?.content" description="没有正文" />
        </el-tab-pane>

        <el-tab-pane :label="`版本历史 (${contentList.length})`" name="version">
          <el-table :data="contentList" border>
            <el-table-column label="版本" align="center" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.draft" type="warning" size="small">草稿</el-tag>
                <span v-else>v{{ row.version }}</span>
              </template>
            </el-table-column>
            <el-table-column label="标题" prop="title" min-width="160" show-overflow-tooltip />
            <el-table-column label="修改人" align="center" prop="editedBy" width="100" />
            <el-table-column label="修改时间" align="center" width="170">
              <template #default="{ row }">{{ formatDate(row.editedDate) }}</template>
            </el-table-column>
            <el-table-column label="当前" align="center" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.current" type="success" size="small">当前</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="100">
              <template #default="{ row }">
                <el-button link type="primary" @click="loadVersion(row.version)">
                  查看该版
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="contentList.length === 0" description="章节没有版本记录" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>

  <!-- 移动弹窗 -->
  <el-dialog v-model="moveVisible" title="移动文档" width="520px">
    <el-form label-width="90px">
      <el-form-item label="当前">
        <span>{{ moveRow?.title }}</span>
      </el-form-item>
      <el-form-item label="目标文档库">
        <el-select v-model="moveForm.lib" class="w-full" @change="onMoveLibChange">
          <el-option v-for="lib in libList" :key="lib.id" :label="lib.name" :value="lib.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="目标章节">
        <el-tree-select
          v-model="moveForm.parent"
          :data="moveChapterTree"
          :props="{ label: 'title', value: 'id', children: 'children' }"
          node-key="id"
          check-strictly
          clearable
          class="w-full"
          placeholder="不选则移到库根"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="moveVisible = false">取消</el-button>
      <el-button type="primary" :loading="moving" @click="handleMove">确定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as DocApi from '@/api/zentao/doc'
import * as ProductApi from '@/api/zentao/product'
import * as ProjectApi from '@/api/zentao/project'
import * as ExecutionApi from '@/api/zentao/execution'
import { formatDate } from '@/utils/formatTime'
import DocForm from './DocForm.vue'
import DocLibDialog from './DocLibDialog.vue'
import { DOC_TYPE_OPTIONS, DOC_STATUS_OPTIONS, tagOf, iconOf } from './constants'

defineOptions({ name: 'ZentaoDoc' })

const message = useMessage()
const loading = ref(false)
const total = ref(0)
const list = ref<DocApi.DocVO[]>([])
const libList = ref<DocApi.DocLibVO[]>([])
const chapterTree = ref<DocApi.DocVO[]>([])
const formRef = ref()
const libDialogRef = ref()

const queryParams = reactive({
  pageNo: 1,
  pageSize: 20,
  lib: undefined as number | undefined,
  parent: undefined as number | undefined,
  title: '',
  type: undefined as string | undefined,
  status: undefined as string | undefined,
  // 章节不是文档，默认从列表里排除；章节通过左侧树和「新建章节」管理
  excludeChapter: true
})

const currentLibName = computed(
  () => libList.value.find((l) => l.id === queryParams.lib)?.name ?? ''
)

/** 把所有库拉全：产品库 + 项目库 + 执行库 + 自定义空间下的库 */
const loadLibs = async () => {
  const [products, projects, executionPage] = await Promise.all([
    ProductApi.getProductSimpleList(),
    ProjectApi.getProjectSimpleList(),
    // 执行没有 simple-list 接口，用分页接口取前 100 条（文档库数量本来就不多）
    ExecutionApi.getExecutionPage({ pageNo: 1, pageSize: 100 })
  ])
  const executions = executionPage.list ?? []
  const groups = await Promise.all([
    ...products.map((p: any) => DocApi.getDocLibList({ type: 'product', objectID: p.id })),
    ...projects.map((p: any) => DocApi.getDocLibList({ type: 'project', objectID: p.id })),
    ...executions.map((e: any) => DocApi.getDocLibList({ type: 'execution', objectID: e.id }))
  ])
  const spaces = await DocApi.getDocLibList({ parent: 0 })
  const subLibs = await Promise.all(spaces.map((s) => DocApi.getDocLibList({ parent: s.id })))
  libList.value = [...groups.flat(), ...spaces, ...subLibs.flat()]
}

const loadChapterTree = async () => {
  chapterTree.value = queryParams.lib ? await DocApi.getChapterTree(queryParams.lib) : []
}

const getList = async () => {
  loading.value = true
  try {
    const data = await DocApi.getDocPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = async () => {
  queryParams.pageNo = 1
  await Promise.all([getList(), loadChapterTree()])
}

const resetQuery = async () => {
  queryParams.lib = undefined
  queryParams.parent = undefined
  queryParams.title = ''
  queryParams.type = undefined
  queryParams.status = undefined
  await handleQuery()
}

const filterByChapter = async (node: DocApi.DocVO) => {
  queryParams.parent = node.id
  queryParams.pageNo = 1
  await getList()
}

const openForm = (type: 'create' | 'edit', row?: DocApi.DocVO, defaultType?: string) => {
  formRef.value.open({
    type,
    row,
    libs: libList.value,
    defaultLib: queryParams.lib,
    defaultParent: queryParams.parent,
    defaultType
  })
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<DocApi.DocVO>()
const contentList = ref<DocApi.DocContentVO[]>([])
const viewingVersion = ref(0)
const activeTab = ref('content')

const openDetail = async (row: DocApi.DocVO) => {
  detailVisible.value = true
  activeTab.value = 'content'
  viewingVersion.value = 0
  detailLoading.value = true
  try {
    // 用 view 接口：已发布的文档会顺带把浏览计数 +1
    detail.value = await DocApi.viewDoc(row.id!)
    contentList.value = await DocApi.getDocContentList(row.id!)
    // 列表里的浏览数是旧的，同步成本行
    row.views = detail.value.views
  } finally {
    detailLoading.value = false
  }
}

const loadVersion = async (version: number) => {
  if (!detail.value?.id) return
  viewingVersion.value = version
  activeTab.value = 'content'
  detail.value = await DocApi.getDoc(detail.value.id, version)
}

const handlePublish = async (row: DocApi.DocVO) => {
  await message.confirm(`确认发布草稿「${row.title}」？发布后草稿会升成 v1`)
  await DocApi.publishDoc(row.id!)
  message.success('发布成功')
  await getList()
}

const handleDelete = async (row: DocApi.DocVO) => {
  await message.delConfirm(`确认删除「${row.title}」？章节下还有子节点时会被拒绝`)
  await DocApi.deleteDoc(row.id!)
  message.success('删除成功')
  await getList()
}

// ==================== 移动 ====================
const moveVisible = ref(false)
const moving = ref(false)
const moveRow = ref<DocApi.DocVO>()
const moveChapterTree = ref<DocApi.DocVO[]>([])
const moveForm = reactive({ lib: undefined as number | undefined, parent: 0 as number })

const openMove = async (row: DocApi.DocVO) => {
  moveRow.value = row
  moveForm.lib = row.lib
  moveForm.parent = 0
  moveChapterTree.value = await DocApi.getChapterTree(row.lib!)
  moveVisible.value = true
}

const onMoveLibChange = async (lib: number) => {
  moveForm.parent = 0
  moveChapterTree.value = await DocApi.getChapterTree(lib)
}

const handleMove = async () => {
  moving.value = true
  try {
    await DocApi.moveDoc({
      id: moveRow.value!.id!,
      lib: moveForm.lib!,
      parent: moveForm.parent ?? 0
    })
    message.success('移动成功')
    moveVisible.value = false
    await handleQuery()
  } finally {
    moving.value = false
  }
}

const openLibDialog = () => libDialogRef.value.open(libList.value)

onMounted(async () => {
  await loadLibs()
  await getList()
})
</script>

<style scoped>
.doc-content {
  white-space: pre-wrap;
  line-height: 1.7;
}
.doc-markdown {
  white-space: pre-wrap;
  line-height: 1.7;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 4px;
}
</style>
