<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="代码库：把「提交」和「需求/任务/缺陷」接起来"
      description="本实现只支持「本地 Git 仓库」：填一个服务器上可访问的 git 仓库路径，点同步即执行 git log（增量）——提交写进 zt_repohistory、改动文件写进 zt_repofiles，提交说明里的 Story #1 / Task #2,3 / Bug #4 会写成对象关联，于是「这个需求是哪几次提交做完的」可以直接反查。GitLab/Gitea 等服务商接入属于另一层（module/provider），未迁移。"
    />

    <el-form class="-mb-15px" :inline="true" label-width="80px">
      <el-form-item label="名称">
        <el-input v-model="queryParams.name" placeholder="代码库名称" clearable class="!w-200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-140px">
          <el-option label="正常" value="active" />
          <el-option label="已关闭" value="closed" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:repo:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增代码库
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" align="center" prop="id" width="70" />
      <el-table-column label="名称" prop="name" min-width="150" show-overflow-tooltip />
      <el-table-column label="仓库路径" prop="path" min-width="220" show-overflow-tooltip />
      <el-table-column label="默认分支" prop="defaultBranch" width="100" align="center" />
      <el-table-column label="关联产品" prop="product" width="100" align="center" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
            {{ row.status === 'active' ? '正常' : '已关闭' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="同步" min-width="230">
        <template #default="{ row }">
          <template v-if="row.synced">
            <el-tag type="success" size="small" class="mr-5px">已同步</el-tag>
            <span class="text-12px text-gray-500">
              {{ (row.lastSyncRevision || '').slice(0, 8) }} · {{ row.lastSyncCount }} 条 · {{ formatDate(row.lastSyncDate) }}
            </span>
          </template>
          <el-tag v-else type="info" size="small">未同步</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openCommits(row)" v-hasPermi="['zentao:repo:query']">提交记录</el-button>
          <el-button link type="success" @click="handleSync(row)" v-hasPermi="['zentao:repo:sync']">同步</el-button>
          <el-button link type="primary" @click="openForm('edit', row)" v-hasPermi="['zentao:repo:update']">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)" v-hasPermi="['zentao:repo:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize"
                @pagination="getList" />
  </ContentWrap>

  <!-- 新增/编辑 -->
  <el-dialog v-model="formVisible" :title="formTitle" width="620px" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="唯一，例如 zentao" />
      </el-form-item>
      <el-form-item label="仓库路径" prop="path">
        <el-input v-model="form.path" placeholder="服务器上可访问的本地 git 仓库，例如 /opt/repos/zentao" />
        <div class="text-12px text-gray-500">必须是存在且含 .git 的目录；本实现不支持远程服务商</div>
      </el-form-item>
      <el-form-item label="默认分支">
        <el-input v-model="form.defaultBranch" placeholder="master" />
      </el-form-item>
      <el-form-item label="关联产品">
        <el-input v-model="form.product" placeholder="产品编号，逗号分隔，例如 1,2" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.desc" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取 消</el-button>
      <el-button type="primary" :loading="saving" @click="submitForm">确 定</el-button>
    </template>
  </el-dialog>

  <!-- 提交记录 -->
  <el-drawer v-model="commitVisible" :title="`提交记录：${currentName}`" size="900px" append-to-body>
    <div v-loading="commitLoading">
      <el-table :data="commits" size="small" empty-text="还没有提交，点列表里的「同步」拉一次" @row-click="openCommit">
        <el-table-column label="序号" prop="commit" width="70" align="center" />
        <el-table-column label="提交说明" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ firstLine(row.comment) }}</template>
        </el-table-column>
        <el-table-column label="提交者" prop="committer" width="100" align="center" />
        <el-table-column label="时间" width="160" align="center">
          <template #default="{ row }">{{ formatDate(row.time) }}</template>
        </el-table-column>
        <el-table-column label="文件" prop="fileCount" width="70" align="center" />
        <el-table-column label="sha" width="100" align="center">
          <template #default="{ row }"><code>{{ row.revision.slice(0, 8) }}</code></template>
        </el-table-column>
      </el-table>
      <Pagination :total="commitTotal" v-model:page="commitQuery.pageNo" v-model:limit="commitQuery.pageSize"
                  @pagination="loadCommits" />
    </div>
  </el-drawer>

  <!-- 提交详情 -->
  <el-dialog v-model="detailVisible" :title="`提交 ${(detail.revision || '').slice(0, 8)}`" width="720px" append-to-body>
    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="提交说明">
        <pre class="commit-comment">{{ detail.comment }}</pre>
      </el-descriptions-item>
      <el-descriptions-item label="提交者 / 时间">
        {{ detail.committer }} · {{ formatDate(detail.time) }}
      </el-descriptions-item>
      <el-descriptions-item label="关联对象">
        <template v-if="(detail.linkedObjects || []).length">
          <el-tag v-for="o in detail.linkedObjects" :key="o.objectType + o.objectID" class="mr-5px" size="small">
            {{ OBJECT_LABEL[o.objectType] || o.objectType }} #{{ o.objectID }} {{ o.objectName }}
          </el-tag>
        </template>
        <span v-else class="text-gray-400">提交说明里没有 Story #id / Task #id / Bug #id</span>
      </el-descriptions-item>
      <el-descriptions-item label="改动文件">
        <el-table :data="detail.files || []" size="small">
          <el-table-column label="动作" width="70" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="ACTION_TAG[row.action] || 'info'">{{ ACTION_LABEL[row.action] || row.action }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="路径" min-width="240">
            <template #default="{ row }">
              <span v-if="row.action === 'R'" class="text-gray-400">{{ row.oldPath }} → </span>{{ row.path }}
            </template>
          </el-table-column>
        </el-table>
      </el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as RepoApi from '@/api/zentao/repo'
// 后端的时间字段是**时间戳**（yudao 的 Jackson 配置），不能当字符串 replace —— 必须用 formatDate
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'ZentaoRepo' })

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const list = ref<RepoApi.RepoVO[]>([])
const total = ref(0)
const queryParams = reactive({ pageNo: 1, pageSize: 10, name: '', status: '' })

const formVisible = ref(false)
const formTitle = ref('')
const formRef = ref()
const form = ref<RepoApi.RepoVO>({ name: '', path: '', defaultBranch: 'master', product: '', desc: '' })
const rules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  path: [{ required: true, message: '仓库路径不能为空', trigger: 'blur' }]
}

const commitVisible = ref(false)
const commitLoading = ref(false)
const commits = ref<RepoApi.RepoCommitVO[]>([])
const commitTotal = ref(0)
const commitQuery = reactive({ pageNo: 1, pageSize: 20, repo: undefined as number | undefined })
const currentName = ref('')

const detailVisible = ref(false)
const detail = ref<Partial<RepoApi.RepoCommitVO>>({})

const OBJECT_LABEL: Record<string, string> = { story: '需求', task: '任务', bug: '缺陷' }
const ACTION_LABEL: Record<string, string> = { A: '新增', M: '修改', D: '删除', R: '重命名' }
// el-tag 的 type 是字面量联合，用 Record<string, string> 会被 vue-tsc 报 TS2322（报表那轮踩过同一个）
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'
const ACTION_TAG: Record<string, TagType> = { A: 'success', M: 'primary', D: 'danger', R: 'warning' }

const firstLine = (comment?: string) => (comment || '').split('\n')[0]

const getList = async () => {
  loading.value = true
  try {
    const data = await RepoApi.getRepoPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryParams.name = ''
  queryParams.status = ''
  handleQuery()
}

const openForm = (type: string, row?: RepoApi.RepoVO) => {
  form.value = type === 'edit' && row
    ? { ...row }
    : { name: '', path: '', defaultBranch: 'master', product: '', desc: '' }
  formTitle.value = type === 'edit' ? '编辑代码库' : '新增代码库'
  formVisible.value = true
}

const submitForm = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.value.id) {
      await RepoApi.updateRepo(form.value)
      message.success('已保存')
    } else {
      await RepoApi.createRepo(form.value)
      message.success('已创建')
    }
    formVisible.value = false
    await getList()
  } finally {
    saving.value = false
  }
}

const handleSync = async (row: RepoApi.RepoVO) => {
  const count = await RepoApi.syncRepo(row.id!)
  message.success(`同步完成：新入库 ${count} 条提交`)
  await getList()
}

const handleDelete = async (row: RepoApi.RepoVO) => {
  await message.delConfirm(`确认删除代码库「${row.name}」？提交记录与改动文件会一起清理`)
  await RepoApi.deleteRepo(row.id!)
  message.success('已删除')
  await getList()
}

const openCommits = async (row: RepoApi.RepoVO) => {
  currentName.value = row.name
  commitQuery.repo = row.id
  commitQuery.pageNo = 1
  commitVisible.value = true
  await loadCommits()
}

const loadCommits = async () => {
  commitLoading.value = true
  try {
    const data = await RepoApi.getCommitPage(commitQuery)
    commits.value = data.list
    commitTotal.value = data.total
  } finally {
    commitLoading.value = false
  }
}

const openCommit = async (row: RepoApi.RepoCommitVO) => {
  detail.value = await RepoApi.getCommit(row.repo, row.revision)
  detailVisible.value = true
}

onMounted(getList)
</script>

<style scoped>
.commit-comment {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
}
</style>
