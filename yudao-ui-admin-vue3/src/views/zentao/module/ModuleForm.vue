<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="620">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="模块树是「一棵树 + 一个类型」"
      description="禅道用 (root, type, branch) 定位一棵模块树：需求/缺陷树挂产品，任务树挂执行。path 用逗号格式，层级由系统自动维护。"
    />
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="110px">
      <el-form-item label="树类型" prop="type">
        <el-select v-model="formData.type" placeholder="请选择类型" class="w-full" :disabled="formType === 'update'"
                   @change="handleTypeChange">
          <el-option v-for="t in typeList" :key="t.type" :label="t.name" :value="t.type" />
        </el-select>
      </el-form-item>

      <el-form-item :label="rootLabel" prop="root">
        <el-select v-model="formData.root" placeholder="请选择" filterable class="w-full" :disabled="formType === 'update'"
                   @change="handleRootChange">
          <el-option v-for="o in rootOptions" :key="o.id" :label="o.name" :value="o.id" />
        </el-select>
      </el-form-item>

      <el-form-item v-if="branchAware" label="分支/平台" prop="branch">
        <el-select v-model="formData.branch" placeholder="主干" clearable filterable class="w-full">
          <el-option label="主干" :value="0" />
          <el-option v-for="b in branchList" :key="b.id" :label="b.name" :value="b.id!" />
        </el-select>
      </el-form-item>

      <el-form-item label="上级模块" prop="parent">
        <el-tree-select
          v-model="formData.parent"
          :data="parentTreeData"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          node-key="id"
          check-strictly
          clearable
          placeholder="不选则为一级模块"
          class="w-full"
        />
      </el-form-item>

      <el-row>
        <el-col :span="14">
          <el-form-item label="模块名称" prop="name">
            <el-input v-model="formData.name" placeholder="如 用户中心" maxlength="60" />
          </el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label="简称" prop="shortName">
            <el-input v-model="formData.shortName" placeholder="选填" maxlength="60" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="负责人" prop="owner">
            <el-input v-model="formData.owner" placeholder="账号" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="排序" prop="order">
            <el-input-number v-model="formData.order" :min="0" class="w-full" placeholder="留空自动取同级最大值+10" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ModuleApi from '@/api/zentao/module'
import * as ProductApi from '@/api/zentao/product'
import * as ExecutionApi from '@/api/zentao/execution'
import * as BranchApi from '@/api/zentao/branch'
import { isBranchAware, isProductRoot } from './constants'

defineOptions({ name: 'ZentaoModuleForm' })

const message = useMessage()
const { t } = useI18n()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formRef = ref()
const typeList = ref<ModuleApi.ModuleTypeVO[]>([])
const productList = ref<ProductApi.ProductSimpleVO[]>([])
const executionList = ref<ExecutionApi.ExecutionVO[]>([])
const branchList = ref<BranchApi.BranchVO[]>([])
const moduleList = ref<ModuleApi.ModuleVO[]>([])

const formData = ref<ModuleApi.ModuleVO>({})

const branchAware = computed(() => isBranchAware(formData.value.type))
const rootLabel = computed(() => (isProductRoot(formData.value.type) ? '所属产品' : '所属执行'))

// 根对象下拉：产品树的 root 是产品，任务树的 root 是执行，产品线的 root 恒为 0
const rootOptions = computed(() => {
  if (formData.value.type === 'line') return [{ id: 0, name: '产品线（root=0）' }]
  if (isProductRoot(formData.value.type)) return productList.value.map((p) => ({ id: p.id, name: p.name }))
  return executionList.value.map((e) => ({ id: e.id!, name: e.name || '' }))
})

// 可选上级：同树同分支的模块，排除自己和自己的子孙（避免把模块移到自己下面）
const parentTreeData = computed(() => {
  const self = formData.value.id
  const selfPrefix = self ? `${formData.value.path || ''}${self},` : ''
  const usable = moduleList.value.filter((m) => {
    if (!self) return true
    if (m.id === self) return false
    return !(m.path || '').startsWith(selfPrefix)
  })
  const build = (parent: number): any[] =>
    usable
      .filter((m) => (m.parent || 0) === parent)
      .map((m) => ({ id: m.id, name: m.name, children: build(m.id!) }))
  return build(0)
})

const handleTypeChange = async () => {
  formData.value.root = undefined
  formData.value.parent = 0
  formData.value.branch = 0
  moduleList.value = []
  branchList.value = []
}

const handleRootChange = async () => {
  formData.value.parent = 0
  await loadModules()
}

const loadModules = async () => {
  if (!formData.value.root && formData.value.type !== 'line') {
    moduleList.value = []
    return
  }
  moduleList.value = await ModuleApi.getModuleList({
    root: formData.value.type === 'line' ? 0 : formData.value.root!,
    type: formData.value.type!,
    branch: branchAware.value ? formData.value.branch ?? 0 : undefined
  })
  if (branchAware.value && formData.value.root) {
    branchList.value = await BranchApi.getBranchListByProduct(formData.value.root)
  }
}

const formRules = reactive({
  type: [{ required: true, message: '树类型不能为空', trigger: 'change' }],
  root: [{ required: true, message: '所属根对象不能为空', trigger: 'change' }],
  name: [{ required: true, message: '模块名称不能为空', trigger: 'blur' }]
})

const open = async (type: string, id?: number, parentId?: number) => {
  dialogVisible.value = true
  formType.value = type
  dialogTitle.value = type === 'create' ? '新建模块' : '编辑模块'
  resetForm()
  typeList.value = await ModuleApi.getModuleTypeList()
  productList.value = await ProductApi.getProductSimpleList()
  const execPage = await ExecutionApi.getExecutionPage({ pageNo: 1, pageSize: 100 })
  executionList.value = execPage.list

  if (id) {
    formLoading.value = true
    try {
      const data = await ModuleApi.getModule(id)
      formData.value = { ...data }
      await loadModules()
    } finally {
      formLoading.value = false
    }
  } else {
    formData.value.type = 'story'
    if (parentId) {
      const parent = await ModuleApi.getModule(parentId)
      formData.value = {
        type: parent.type,
        root: parent.root,
        branch: parent.branch,
        parent: parent.id
      }
      await loadModules()
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
    const payload = { ...formData.value }
    if (!branchAware.value) payload.branch = 0
    if (formType.value === 'create') {
      await ModuleApi.createModule(payload)
      message.success(t('common.createSuccess'))
    } else {
      await ModuleApi.updateModule(payload)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = { type: 'story', branch: 0, parent: 0 }
  moduleList.value = []
  branchList.value = []
  formRef.value?.resetFields()
}
</script>
