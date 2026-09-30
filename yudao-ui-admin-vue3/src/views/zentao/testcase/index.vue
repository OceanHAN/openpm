<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="测试用例：头部 + 版本快照 + 步骤"
      description="只有「步骤」变化才会升版本并把状态打回「待评审」；标题/前置条件/优先级/状态都是原地修改。用例关联需求时会把需求当前版本冻结下来，需求以后升版，这里会出现「需求已变更」提示，需要手动确认。"
    />

    <el-form class="-mb-15px" :inline="true" label-width="86px">
      <el-form-item label="所属产品">
        <el-select v-model="queryParams.product" class="!w-180px" filterable placeholder="全部产品" @change="handleQuery">
          <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="分支/平台">
        <BranchSelect :model-value="queryParams.branch" :product="queryParams.product" @update:model-value="(v) => { queryParams.branch = v; handleQuery() }" />
      </el-form-item>
      <el-form-item label="所属模块">
        <ModuleSelect
          v-model="queryParams.module"
          :root="queryParams.product"
          type="case"
          :branch="queryParams.branch"
          @update:model-value="handleQuery"
        />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="queryParams.type" class="!w-140px" clearable placeholder="全部" @change="handleQuery">
          <el-option v-for="o in CASE_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="环节">
        <el-select v-model="queryParams.stage" class="!w-160px" clearable placeholder="全部" @change="handleQuery">
          <el-option v-for="o in CASE_STAGE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" class="!w-140px" clearable placeholder="全部" @change="handleQuery">
          <el-option v-for="o in CASE_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
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
      <el-form-item label="待确认">
        <el-switch v-model="queryParams.needConfirm" @change="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:testcase:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新建用例
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" empty-text="没有符合条件的用例">
      <el-table-column label="标题" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.title }}</el-link>
          <el-tag v-if="row.needConfirm" type="warning" size="small" class="ml-5px">需求已变更</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关联需求" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.story">#{{ row.story }} {{ row.storyTitle }}</span>
          <span v-else class="text-gray-400">-</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" align="center" width="100" prop="typeName" />
      <el-table-column label="环节" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ stageNames(row.stage) }}</template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="pri" width="80" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="{ row }">
          <el-tag :type="tagOf(CASE_STATUS_OPTIONS, row.status) as any">{{ row.statusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="版本" align="center" width="70">
        <template #default="{ row }">v{{ row.version }}</template>
      </el-table-column>
      <el-table-column label="修改人" align="center" prop="lastEditedBy" width="100" />
      <el-table-column label="修改时间" align="center" width="170">
        <template #default="{ row }">{{ formatDate(row.lastEditedDate) }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          <el-button link type="primary" @click="openForm('edit', row)" v-hasPermi="['zentao:testcase:update']">
            编辑
          </el-button>
          <el-button
            v-if="row.status === 'wait'"
            link
            type="success"
            @click="openReview(row)"
            v-hasPermi="['zentao:testcase:update']"
          >
            评审
          </el-button>
          <el-button
            v-if="row.needConfirm"
            link
            type="warning"
            @click="handleConfirmStory(row)"
            v-hasPermi="['zentao:testcase:update']"
          >
            确认需求
          </el-button>
          <el-button link type="danger" @click="handleDelete(row)" v-hasPermi="['zentao:testcase:delete']">
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

  <CaseForm ref="formRef" @success="getList" />

  <!-- 评审弹窗 -->
  <el-dialog v-model="reviewVisible" title="评审用例" width="480px">
    <el-form label-width="80px">
      <el-form-item label="用例">{{ reviewRow?.title }}</el-form-item>
      <el-form-item label="评审结果">
        <el-radio-group v-model="reviewForm.result">
          <el-radio-button v-for="o in CASE_REVIEW_OPTIONS" :key="o.value" :value="o.value">
            {{ o.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="评审意见">
        <el-input v-model="reviewForm.comment" type="textarea" :rows="3" placeholder="可选" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="reviewVisible = false">取消</el-button>
      <el-button type="primary" :loading="reviewing" :disabled="!reviewForm.result" @click="handleReview">
        提交评审
      </el-button>
    </template>
  </el-dialog>

  <!-- 详情抽屉：步骤 + 版本历史 -->
  <el-drawer v-model="detailVisible" :title="`用例详情 #${detail?.id ?? ''}`" size="60%">
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
        <el-descriptions-item label="关联需求" :span="2">
          <span v-if="detail?.story">
            #{{ detail?.story }} {{ detail?.storyTitle }}
            <el-tag v-if="detail?.needConfirm" type="warning" size="small" class="ml-5px">
              需求已升到 v{{ detail?.latestStoryVersion }}，本用例冻结在 v{{ detail?.storyVersion }}
            </el-tag>
          </span>
          <span v-else class="text-gray-400">-</span>
        </el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail?.typeName }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ detail?.pri }}</el-descriptions-item>
        <el-descriptions-item label="环节">{{ stageNames(detail?.stage) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="tagOf(CASE_STATUS_OPTIONS, detail?.status) as any">{{ detail?.statusName }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="当前版本">v{{ detail?.version }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail?.openedBy }}</el-descriptions-item>
        <el-descriptions-item label="评审人">{{ detail?.reviewedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="前置条件" :span="2">
          {{ detail?.precondition || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="activeTab">
        <el-tab-pane :label="`步骤 (${detail?.steps?.length ?? 0})`" name="steps">
          <el-table :data="detail?.steps ?? []" border size="small">
            <el-table-column label="编号" align="center" width="90" prop="name" />
            <el-table-column label="步骤" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag v-if="row.type === 'group'" type="warning" size="small" class="mr-5px">步骤组</el-tag>
                {{ row.desc }}
              </template>
            </el-table-column>
            <el-table-column label="预期结果" min-width="200" show-overflow-tooltip prop="expect" />
          </el-table>
          <el-empty v-if="!(detail?.steps?.length)" description="该版本还没有步骤" />
        </el-tab-pane>

        <el-tab-pane :label="`版本历史 (${specList.length})`" name="version">
          <el-table :data="specList" border>
            <el-table-column label="版本" align="center" width="80">
              <template #default="{ row }">v{{ row.version }}</template>
            </el-table-column>
            <el-table-column label="标题" prop="title" min-width="200" show-overflow-tooltip />
            <el-table-column label="步骤数" align="center" prop="stepCount" width="90" />
            <el-table-column label="当前" align="center" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.current" type="success" size="small">当前</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="110">
              <template #default="{ row }">
                <el-button link type="primary" @click="loadVersion(row.version)">查看该版</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<script lang="ts" setup>
import * as CaseApi from '@/api/zentao/testcase'
import * as ProductApi from '@/api/zentao/product'
import { formatDate } from '@/utils/formatTime'
import CaseForm from './CaseForm.vue'
import BranchSelect from '../components/BranchSelect.vue'
import ModuleSelect from '../components/ModuleSelect.vue'
import { CASE_TYPE_OPTIONS, CASE_STAGE_OPTIONS, CASE_STATUS_OPTIONS, CASE_REVIEW_OPTIONS, tagOf, stageNames } from './constants'

defineOptions({ name: 'ZentaoTestcase' })

const message = useMessage()
const loading = ref(false)
const list = ref<CaseApi.CaseVO[]>([])
const total = ref(0)
const productList = ref<any[]>([])
const formRef = ref()

const queryParams = reactive({
  pageNo: 1,
  pageSize: 20,
  product: undefined as number | undefined,
  branch: undefined as number | undefined,
  module: undefined as number | undefined,
  type: undefined as string | undefined,
  stage: undefined as string | undefined,
  status: undefined as string | undefined,
  title: '',
  needConfirm: false
})

const getList = async () => {
  loading.value = true
  try {
    const data = await CaseApi.getCasePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  return getList()
}

const resetQuery = () => {
  queryParams.product = undefined
  queryParams.branch = undefined
  queryParams.module = undefined
  queryParams.type = undefined
  queryParams.stage = undefined
  queryParams.status = undefined
  queryParams.title = ''
  queryParams.needConfirm = false
  return handleQuery()
}

const openForm = (type: 'create' | 'edit', row?: CaseApi.CaseVO) => {
  formRef.value.open({ type, row, product: queryParams.product })
}

const handleDelete = async (row: CaseApi.CaseVO) => {
  await message.delConfirm(`确认删除用例「${row.title}」？步骤与版本快照会一起清理`)
  await CaseApi.deleteCase(row.id!)
  message.success('删除成功')
  await getList()
}

const handleConfirmStory = async (row: CaseApi.CaseVO) => {
  await message.confirm(
    `需求已升到 v${row.latestStoryVersion}，本用例冻结在 v${row.storyVersion}。确认后本用例会追平到最新版本`
  )
  await CaseApi.confirmStoryChange(row.id!)
  message.success('已确认需求变更')
  await getList()
}

// ==================== 评审 ====================
const reviewVisible = ref(false)
const reviewing = ref(false)
const reviewRow = ref<CaseApi.CaseVO>()
const reviewForm = reactive({ result: '', comment: '' })

const openReview = (row: CaseApi.CaseVO) => {
  reviewRow.value = row
  reviewForm.result = ''
  reviewForm.comment = ''
  reviewVisible.value = true
}

const handleReview = async () => {
  reviewing.value = true
  try {
    await CaseApi.reviewCase({ id: reviewRow.value!.id!, result: reviewForm.result, comment: reviewForm.comment })
    message.success('评审完成')
    reviewVisible.value = false
    await getList()
  } finally {
    reviewing.value = false
  }
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<CaseApi.CaseVO>()
const specList = ref<CaseApi.CaseSpecVO[]>([])
const viewingVersion = ref(0)
const activeTab = ref('steps')

const openDetail = async (row: CaseApi.CaseVO) => {
  detailVisible.value = true
  activeTab.value = 'steps'
  viewingVersion.value = 0
  detailLoading.value = true
  try {
    detail.value = await CaseApi.getCase(row.id!, 0)
    specList.value = await CaseApi.getCaseSpecList(row.id!)
  } finally {
    detailLoading.value = false
  }
}

const loadVersion = async (version: number) => {
  if (!detail.value?.id) return
  viewingVersion.value = version
  activeTab.value = 'steps'
  detail.value = await CaseApi.getCase(detail.value.id, version)
}

onMounted(async () => {
  productList.value = await ProductApi.getProductSimpleList()
  await getList()
})
</script>
