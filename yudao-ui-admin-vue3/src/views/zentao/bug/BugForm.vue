<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="720">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="90px">
      <el-form-item label="缺陷标题" prop="title">
        <el-input v-model="formData.title" placeholder="请输入缺陷标题" maxlength="255" show-word-limit />
      </el-form-item>

      <el-row>
        <el-col :span="8">
          <el-form-item label="所属产品" prop="product">
            <el-input-number v-model="formData.product" :min="1" :controls="false" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="所属项目" prop="project">
            <el-input-number v-model="formData.project" :min="0" :controls="false" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="关联需求" prop="story">
            <el-input-number v-model="formData.story" :min="0" :controls="false" class="w-full" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col v-if="branchVisible" :span="8">
          <el-form-item label="分支/平台" prop="branch">
            <ZentaoBranchSelect v-model="formData.branch" :product="formData.product" type="bug" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="所属模块" prop="module">
            <ZentaoModuleSelect
              v-model="formData.module"
              :root="formData.product"
              type="bug"
              :branch="formData.branch ?? 0"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="8">
          <el-form-item label="严重程度" prop="severity">
            <el-select v-model="formData.severity" class="w-full">
              <el-option v-for="o in BUG_SEVERITY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="优先级" prop="pri">
            <el-select v-model="formData.pri" class="w-full">
              <el-option v-for="o in PRI_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="缺陷类型" prop="type">
            <el-select v-model="formData.type" class="w-full">
              <el-option v-for="o in BUG_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="8">
          <el-form-item label="影响版本" prop="openedBuild">
            <el-input v-model="formData.openedBuild" placeholder="如 v1.0" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="操作系统" prop="os">
            <el-input v-model="formData.os" placeholder="如 Windows 11" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="浏览器" prop="browser">
            <el-input v-model="formData.browser" placeholder="如 Chrome 130" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="指派给" prop="assignedTo">
        <el-input v-model="formData.assignedTo" placeholder="账号，如 admin" />
      </el-form-item>

      <el-form-item label="重现步骤" prop="steps">
        <el-input v-model="formData.steps" type="textarea" :rows="5"
                  placeholder="1. 打开登录页&#10;2. 输入正确账号密码&#10;3. 点击登录&#10;预期：进入首页&#10;实际：返回 500" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as BugApi from '@/api/zentao/bug'
import * as ProductApi from '@/api/zentao/product'
import ZentaoBranchSelect from '@/views/zentao/components/BranchSelect.vue'
import ZentaoModuleSelect from '@/views/zentao/components/ModuleSelect.vue'
import { BUG_SEVERITY_OPTIONS, BUG_TYPE_OPTIONS, PRI_OPTIONS } from './constants'

defineOptions({ name: 'ZentaoBugForm' })

const message = useMessage()
const { t } = useI18n()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formRef = ref()

const formData = ref<BugApi.BugVO>({})
const productList = ref<ProductApi.ProductSimpleVO[]>([])

// 产品类型决定要不要展示「分支/平台」
const branchVisible = computed(() => {
  const hit = productList.value.find((p) => p.id === formData.value.product)
  return hit?.type === 'branch' || hit?.type === 'platform'
})

const formRules = reactive({
  title: [{ required: true, message: '缺陷标题不能为空', trigger: 'blur' }],
  product: [{ required: true, message: '所属产品不能为空', trigger: 'blur' }],
  severity: [{ required: true, message: '严重程度不能为空', trigger: 'change' }],
  pri: [{ required: true, message: '优先级不能为空', trigger: 'change' }]
})

const open = async (type: string, id?: number) => {
  productList.value = await ProductApi.getProductSimpleList()
  dialogVisible.value = true
  dialogTitle.value = type === 'create' ? '新增缺陷' : '编辑缺陷'
  formType.value = type
  resetForm()
  if (id) {
    formLoading.value = true
    try {
      formData.value = await BugApi.getBug(id)
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await BugApi.createBug(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await BugApi.updateBug(formData.value)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = {
    product: 1,
    project: 0,
    branch: 0,
    module: 0,
    story: 0,
    title: '',
    severity: 3,
    pri: 3,
    type: 'codeerror',
    openedBuild: '',
    os: '',
    browser: '',
    assignedTo: '',
    steps: ''
  }
  formRef.value?.resetFields()
}
</script>
