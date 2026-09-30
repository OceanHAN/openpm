<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="保存查询：列表页的「搜索」存下来的条件"
      description="禅道每个列表页都能把当前搜索条件存下来、下次一键用，还能设为快捷方式（显示在页签上）或公共查询（所有人可见）。⚠️ 一处关键差异：禅道把条件序列化成**一段 SQL** 存进 zt_userquery.sql 并直接拼进 WHERE；本实现存的是**结构化条件 JSON**（field/op/value），由各模块的强类型查询 VO 翻译执行 —— 照搬 SQL 等于把注入口子开到数据层。"
    />

    <el-form class="-mb-15px" :inline="true" label-width="70px">
      <el-form-item label="模块">
        <el-select v-model="query.module" clearable placeholder="全部" class="!w-160px" @change="handleQuery">
          <el-option v-for="m in MODULES" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="query.title" placeholder="查询名称" clearable class="!w-180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="账号">
        <el-input v-model="query.account" placeholder="不填=我的查询" clearable class="!w-160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 查询</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:search:save']">
          <Icon icon="ep:plus" class="mr-5px" /> 新建查询
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" empty-text="还没有保存的查询">
      <el-table-column label="编号" prop="id" width="70" align="center" />
      <el-table-column label="名称" prop="title" min-width="150" show-overflow-tooltip />
      <el-table-column label="模块" width="110">
        <template #default="{ row }">
          <el-tag size="small">{{ MODULE_LABEL[row.module] || row.module }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="账号" prop="account" width="110" />
      <el-table-column label="快捷方式" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.shortcut === 1 ? 'success' : 'info'" size="small">{{ row.shortcut === 1 ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="公共" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.common === 1 ? 'warning' : 'info'" size="small">{{ row.common === 1 ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="条件（结构化 JSON）" min-width="280">
        <template #default="{ row }">
          <span class="font-mono text-12px">{{ row.conditions }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="toggleShortcut(row)" v-hasPermi="['zentao:search:save']">
            {{ row.shortcut === 1 ? '取消快捷' : '设为快捷' }}
          </el-button>
          <el-button link type="primary" @click="openForm('edit', row)" v-hasPermi="['zentao:search:save']">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)" v-hasPermi="['zentao:search:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>

  <ContentWrap>
    <el-card shadow="never">
      <template #header><span class="font-bold">拼音首字母（zt_searchdict 码表）</span></template>
      <el-form :inline="true" label-width="80px">
        <el-form-item label="中文">
          <el-input v-model="pinyinText" placeholder="例如：需求" class="!w-200px" @keyup.enter="runPinyin" />
        </el-form-item>
        <el-form-item>
          <el-button @click="runPinyin"><Icon icon="ep:magic-stick" class="mr-5px" /> 取首字母</el-button>
        </el-form-item>
        <el-form-item v-if="pinyin">
          <el-tag type="success">{{ pinyin }}</el-tag>
        </el-form-item>
      </el-form>
      <div class="text-12px text-gray-400">
        禅道用它实现「按拼音搜中文」：`zt_searchdict` 是码表（key=汉字 Unicode 码点，value=一位字母）。
        本项目只迁了码表与查询接口，**跨对象的全文检索（zt_searchindex + InnoDB FULLTEXT）没有做** —— 见 README。
      </div>
    </el-card>
  </ContentWrap>

  <el-dialog v-model="formVisible" :title="formTitle" width="680px" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="模块" prop="module">
        <el-select v-model="form.module" class="!w-220px" placeholder="选择模块">
          <el-option v-for="m in MODULES" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="查询名称" prop="title">
        <el-input v-model="form.title" placeholder="例如：激活的需求" />
      </el-form-item>
      <el-form-item label="条件 JSON" prop="conditions">
        <el-input
          v-model="form.conditions"
          type="textarea"
          :rows="4"
          placeholder='[{"field":"status","op":"eq","value":"active"}]'
        />
      </el-form-item>
      <el-form-item label="表单快照">
        <el-input v-model="form.form" type="textarea" :rows="2" placeholder="可留空；前端回填搜索表单用" />
      </el-form-item>
      <el-form-item label="快捷方式">
        <el-switch v-model="form.shortcut" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item label="公共查询">
        <el-switch v-model="form.common" :active-value="1" :inactive-value="0" />
        <span class="ml-8px text-12px text-gray-400">公共查询对所有人可见（禅道 common=1 的口径）</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取 消</el-button>
      <el-button type="primary" :loading="saving" @click="submitForm">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as SearchApi from '@/api/zentao/search'

defineOptions({ name: 'ZentaoSearch' })

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const list = ref<SearchApi.SearchQueryVO[]>([])
const total = ref(0)
const query = reactive<any>({ pageNo: 1, pageSize: 10 })
const pinyinText = ref('需求')
const pinyin = ref('')

const MODULES = [
  { label: '需求', value: 'story' },
  { label: '任务', value: 'task' },
  { label: '缺陷', value: 'bug' },
  { label: '用例', value: 'testcase' },
  { label: '项目', value: 'project' },
  { label: '执行', value: 'execution' },
  { label: '产品', value: 'product' },
  { label: '文档', value: 'doc' }
]
const MODULE_LABEL: Record<string, string> = Object.fromEntries(MODULES.map((m) => [m.value, m.label]))

const formVisible = ref(false)
const formTitle = ref('')
const formRef = ref()
const form = ref<SearchApi.SearchQueryVO>({ module: 'story', title: '', conditions: '', shortcut: 0, common: 0 })
const rules = {
  module: [{ required: true, message: '模块不能为空', trigger: 'change' }],
  title: [{ required: true, message: '查询名称不能为空', trigger: 'blur' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await SearchApi.getSearchQueryPage(query)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  query.pageNo = 1
  load()
}

const openForm = (type: string, row?: SearchApi.SearchQueryVO) => {
  form.value =
    type === 'edit' && row
      ? { ...row }
      : { module: query.module || 'story', title: '', conditions: '', shortcut: 0, common: 0 }
  formTitle.value = type === 'edit' ? '编辑保存的查询' : '新建保存的查询'
  formVisible.value = true
}

const submitForm = async () => {
  // Element Plus 的 validate() 失败时是 reject，不 catch 会冒未捕获的 unhandledrejection（坑位 #53）
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await SearchApi.saveSearchQuery(form.value)
    message.success('已保存')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const toggleShortcut = async (row: SearchApi.SearchQueryVO) => {
  await SearchApi.setSearchQueryShortcut(row.id!, row.shortcut === 1 ? 0 : 1)
  message.success(row.shortcut === 1 ? '已取消快捷方式' : '已设为快捷方式')
  await load()
}

const handleDelete = async (row: SearchApi.SearchQueryVO) => {
  await message.delConfirm(`确认删除查询「${row.title}」？`)
  await SearchApi.deleteSearchQuery(row.id!)
  message.success('已删除')
  await load()
}

const runPinyin = async () => {
  pinyin.value = await SearchApi.getPinyinInitials(pinyinText.value)
}

onMounted(load)
</script>
