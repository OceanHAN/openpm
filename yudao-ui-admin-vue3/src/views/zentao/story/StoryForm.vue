<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="720">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="90px"
    >
      <el-row>
        <el-col :span="12">
          <el-form-item label="所属产品" prop="product">
            <el-select v-model="formData.product" placeholder="请选择产品" class="w-full">
              <el-option
                v-for="p in productList"
                :key="p.id"
                :label="p.name"
                :value="p.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="优先级" prop="pri">
            <el-select v-model="formData.pri" placeholder="请选择优先级" class="w-full">
              <el-option v-for="o in STORY_PRI_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="需求类型" prop="type">
            <el-select
              v-model="formData.type"
              placeholder="请选择需求类型"
              class="w-full"
              :disabled="formType === 'update'"
            >
              <el-option v-for="o in storyTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <div v-if="formType === 'update'" class="text-12px text-gray-400">
              需求类型创建后不可修改
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item v-if="branchVisible" label="分支/平台" prop="branch">
            <ZentaoBranchSelect v-model="formData.branch" :product="formData.product" type="story" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="所属模块" prop="module">
        <ZentaoModuleSelect
          v-model="formData.module"
          :root="formData.product"
          type="story"
          :branch="formData.branch ?? 0"
        />
        <div v-if="!formData.product" class="text-12px text-gray-400">先选择产品，再选择模块</div>
      </el-form-item>

      <el-form-item label="所属计划" prop="plan">
        <el-select v-model="formData.plan" placeholder="不选则未排期" clearable filterable class="w-full">
          <el-option v-for="p in planList" :key="p.id" :label="p.title" :value="String(p.id)" />
        </el-select>
        <div v-if="planList.length === 0" class="text-12px text-gray-400">
          该产品还没有计划，可到「计划管理」先创建
        </div>
      </el-form-item>

      <el-form-item label="需求标题" prop="title">
        <el-input v-model="formData.title" placeholder="请输入需求标题" maxlength="255" show-word-limit />
      </el-form-item>

      <el-row>
        <el-col :span="12">
          <el-form-item label="需求分类" prop="category">
            <el-select v-model="formData.category" placeholder="请选择分类" class="w-full">
              <el-option v-for="o in STORY_CATEGORY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="需求来源" prop="source">
            <el-select v-model="formData.source" placeholder="请选择来源" clearable class="w-full">
              <el-option v-for="o in STORY_SOURCE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="预计工时" prop="estimate">
            <el-input-number v-model="formData.estimate" :min="0" :precision="2" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="指派给" prop="assignedTo">
            <el-input v-model="formData.assignedTo" placeholder="请输入账号，如 admin" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="需求描述" prop="spec">
        <el-input v-model="formData.spec" type="textarea" :rows="5" placeholder="请输入需求描述" />
      </el-form-item>

      <el-form-item label="验收标准" prop="verify">
        <el-input v-model="formData.verify" type="textarea" :rows="3" placeholder="请输入验收标准" />
      </el-form-item>

      <el-alert
        v-if="formType === 'update'"
        type="info"
        :closable="false"
        show-icon
        title="普通编辑不会产生新版本"
        description="当前操作会原地修改「当前版本」的描述与验收标准。若要留存历史，请用列表中的「变更」功能。"
      />
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as StoryApi from '@/api/zentao/story'
import * as ProductApi from '@/api/zentao/product'
import * as PlanApi from '@/api/zentao/plan'
import ZentaoBranchSelect from '@/views/zentao/components/BranchSelect.vue'
import ZentaoModuleSelect from '@/views/zentao/components/ModuleSelect.vue'
import {
  STORY_PRI_OPTIONS,
  STORY_CATEGORY_OPTIONS,
  STORY_SOURCE_OPTIONS
} from './constants'

defineOptions({ name: 'ZentaoStoryForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const productList = ref<ProductApi.ProductSimpleVO[]>([])
const planList = ref<PlanApi.PlanVO[]>([])
const formRef = ref()

// 产品类型决定要不要展示「分支/平台」；normal 类型的产品不显示
const branchVisible = computed(() => {
  const hit = productList.value.find((p) => p.id === formData.value.product)
  return hit?.type === 'branch' || hit?.type === 'platform'
})

const formData = ref<StoryApi.StoryVO>({
  id: undefined,
  product: undefined,
  branch: 0,
  module: 0,
  plan: '',
  title: '',
  type: 'story',
  pri: 3,
  category: 'feature',
  source: undefined,
  estimate: 0,
  assignedTo: '',
  spec: '',
  verify: ''
})

// 需求分层类型下拉：优先用后端字典（zentao/story/type-list），拿不到时退回内置三层
const storyTypeOptions = ref<Array<{ label: string; value: string }>>([
  { label: '业务需求', value: 'epic' },
  { label: '用户需求', value: 'requirement' },
  { label: '研发需求', value: 'story' }
])
const loadStoryTypes = async () => {
  try {
    const list = await StoryApi.getStoryTypeList()
    if (list?.length) {
      storyTypeOptions.value = list.map((t) => ({ label: t.name, value: t.type }))
    }
  } catch {
    // 字典接口失败不影响建档，用内置三层兜底
  }
}

const loadPlans = async () => {
  if (!formData.value.product) {
    planList.value = []
    return
  }
  planList.value = await PlanApi.getPlanListByProduct(formData.value.product)
}

// 产品换了以后计划列表要跟着换，同时清掉原来选中的计划（可能不属于新产品）
watch(
  () => formData.value.product,
  async (val, old) => {
    if (old !== undefined && val !== old) formData.value.plan = ''
    await loadPlans()
  }
)

const formRules = reactive({
  product: [{ required: true, message: '所属产品不能为空', trigger: 'change' }],
  title: [{ required: true, message: '需求标题不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '需求类型不能为空', trigger: 'change' }],
  pri: [{ required: true, message: '优先级不能为空', trigger: 'change' }]
})

/** 打开弹窗 */
const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = type === 'create' ? '新增需求' : '编辑需求'
  formType.value = type
  resetForm()
  // 加载产品下拉 + 需求分层字典
  productList.value = await ProductApi.getProductSimpleList()
  await loadStoryTypes()
  if (id) {
    formLoading.value = true
    try {
      const data = await StoryApi.getStory(id)
      formData.value = data
      formData.value.plan = data.plan || ''
      await loadPlans()
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open })

/** 提交表单 */
const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await StoryApi.createStory(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await StoryApi.updateStory(formData.value)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined,
    product: undefined,
    branch: 0,
    module: 0,
    title: '',
    type: 'story',
    pri: 3,
    category: 'feature',
    source: undefined,
    estimate: 0,
    assignedTo: '',
    spec: '',
    verify: ''
  }
  formRef.value?.resetFields()
}
</script>
